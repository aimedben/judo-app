package com.example.data.local.face

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.util.face.EdgeFaceOnnxEmbedder
import com.example.util.face.FaceMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Export / import de la dataset de reconnaissance faciale (vecteurs d'empreintes).
 *
 * Les vecteurs sont stockes CHIFFRES sur l'appareil (cle Keystore non exportable) : un export
 * les dechiffre en memoire, les ecrit dans le fichier JSON choisi par l'admin (SAF), puis les
 * oublie. A l'import, chaque vecteur est re-chiffre avec la cle du NOUVEL appareil : le fichier
 * exporte ne contient donc aucune cle et reste inutilisable tel quel hors de l'application.
 *
 * Aucune photo de visage n'est exportee — uniquement des positions dans l'espace des
 * descripteurs, rattachees au QR code de l'athlete pour retrouver sa fiche sur un autre appareil.
 */
object FaceDatasetTransfer {

    private const val FORMAT = "jcs-face-dataset"
    private const val VERSION = 1

    data class ExportResult(val templates: Int, val members: Int, val vectors: Int)
    data class ImportResult(
        val imported: Int,
        val skippedExisting: Int,
        val unknownMembers: Int,
        val incompatible: Int
    )

    suspend fun export(context: Context, uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        val dao = FaceDatabase.getInstance(context).faceTemplateDao()
        val members = AppDatabase.getInstance(context).memberDao().getAllMembersDirect()
        val byId = members.associateBy { it.id }

        val rows = dao.all()
        val templatesArr = JSONArray()
        var exported = 0
        var membersTouched = HashSet<Long>()

        rows.forEach { row ->
            val plain = FaceCrypto.decrypt(row.embedding, row.iv) ?: return@forEach
            val vector = FaceMath.fromBytes(plain) ?: return@forEach
            val member = byId[row.memberId]
            val obj = JSONObject().apply {
                put("memberId", row.memberId)
                put("qrCode", member?.qrCode ?: "")
                put("fullName", member?.fullName ?: "")
                put("modelVersion", row.modelVersion)
                put("quality", row.qualityScore.toDouble())
                put("createdAt", row.createdAt)
                put("source", row.source)
                put("vector", toJsonArray(vector))
            }
            templatesArr.put(obj)
            exported++
            membersTouched.add(row.memberId)
        }

        val root = JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("templates", templatesArr)
        }

        val payload = root.toString()
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(payload.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: error("Impossible d'écrire le fichier choisi")

        ExportResult(
            templates = exported,
            members = membersTouched.size,
            vectors = exported
        )
    }

    suspend fun import(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: return@withContext ImportResult(0, 0, 0, 0)

        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: return@withContext ImportResult(0, 0, 0, 0)
        if (root.optString("format") != FORMAT) return@withContext ImportResult(0, 0, 0, 0)
        val arr = root.optJSONArray("templates") ?: JSONArray()

        // Version de modele attendue SUR CET APPAREIL : un vecteur produit par un autre
        // modele/resolution serait inutilisable au pointage, on refuse donc ces entrees.
        val embedder = runCatching { EdgeFaceOnnxEmbedder.create(context) }.getOrNull()
        val localModelVersion = embedder?.modelVersion
        val localDimensions = embedder?.dimensions
        embedder?.close()

        val db = AppDatabase.getInstance(context)
        val memberDao = db.memberDao()
        val faceDao = FaceDatabase.getInstance(context).faceTemplateDao()
        val repository = FaceGalleryRepository(faceDao)

        var imported = 0
        var skippedExisting = 0
        var unknown = 0
        var incompatible = 0

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val vector = fromJsonArray(obj.optJSONArray("vector")) ?: continue
            if (localDimensions != null && vector.size != localDimensions) {
                incompatible++
                continue
            }
            val qrCode = obj.optString("qrCode", "")
            val member = if (qrCode.isNotBlank()) memberDao.getMemberByQrCode(qrCode) else null
            if (member == null) {
                unknown++
                continue
            }
            if (faceDao.countForMember(member.id) > 0) {
                skippedExisting++
                continue
            }
            val ok = repository.addTemplate(
                memberId = member.id,
                embedding = vector,
                modelVersion = localModelVersion ?: obj.optString("modelVersion", ""),
                qualityScore = obj.optDouble("quality", 0.0).toFloat(),
                source = obj.optString("source", FaceGalleryRepository.SOURCE_ENROLL_LIVE)
            )
            if (ok) imported++ else incompatible++
        }

        ImportResult(imported, skippedExisting, unknown, incompatible)
    }

    /** Vecteurs actuellement en base (pour afficher le volume dans les paramètres). */
    suspend fun localStats(context: Context): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val dao = FaceDatabase.getInstance(context).faceTemplateDao()
        dao.countAll() to dao.enrolledMemberIds().size
    }

    private fun toJsonArray(vector: FloatArray): JSONArray {
        val arr = JSONArray()
        vector.forEach { v ->
            arr.put((v * 100_000f).roundToInt() / 100_000.0)
        }
        return arr
    }

    private fun fromJsonArray(arr: JSONArray?): FloatArray? {
        if (arr == null || arr.length() == 0) return null
        return FloatArray(arr.length()) { idx -> arr.optDouble(idx, 0.0).toFloat() }
    }
}
