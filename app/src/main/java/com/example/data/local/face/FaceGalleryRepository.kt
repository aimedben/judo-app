package com.example.data.local.face

import com.example.util.face.FaceMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Acces a la galerie d'empreintes. Le dechiffrement n'a lieu qu'en memoire, au demarrage
 * de la seance, et la galerie est liberee a la sortie de l'ecran de pointage.
 */
class FaceGalleryRepository(private val dao: FaceTemplateDao) {

    suspend fun addTemplate(
        memberId: Long,
        embedding: FloatArray,
        modelVersion: String,
        qualityScore: Float,
        source: String = SOURCE_ENROLL_LIVE
    ): Boolean = withContext(Dispatchers.IO) {
        val normalized = FaceMath.l2Normalize(embedding)
        val raw = ByteArray(normalized.size * 4).also { bytes -> FaceMath.toBytes(normalized, bytes) }
        val (encrypted, iv) = FaceCrypto.encrypt(raw) ?: return@withContext false
        dao.insert(
            FaceTemplateEntity(
                memberId = memberId,
                embedding = encrypted,
                iv = iv,
                modelVersion = modelVersion,
                qualityScore = qualityScore,
                createdAt = System.currentTimeMillis(),
                source = source
            )
        )
        true
    }

    /** Galerie dechiffree : memberId -> embeddings normalises. Aucune copie persistee. */
    suspend fun loadGalleryForMembers(
        memberIds: List<Long>,
        modelVersion: String
    ): Map<Long, List<FloatArray>> = withContext(Dispatchers.IO) {
        if (memberIds.isEmpty()) return@withContext emptyMap()
        val rows = dao.forMembers(memberIds).filter { it.modelVersion == modelVersion }
        val gallery = HashMap<Long, MutableList<FloatArray>>()
        rows.forEach { row ->
            val plain = FaceCrypto.decrypt(row.embedding, row.iv) ?: return@forEach
            val vector = FaceMath.fromBytes(plain) ?: return@forEach
            gallery.getOrPut(row.memberId) { mutableListOf() }.add(FaceMath.l2Normalize(vector))
        }
        gallery
    }

    suspend fun getEnrollmentStatus(memberId: Long): Int = withContext(Dispatchers.IO) {
        dao.countForMember(memberId)
    }

    suspend fun enrolledMemberIds(): List<Long> = withContext(Dispatchers.IO) {
        dao.enrolledMemberIds()
    }

    suspend fun deleteForMember(memberId: Long) = withContext(Dispatchers.IO) {
        dao.deleteForMember(memberId)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }

    companion object {
        const val SOURCE_ENROLL_LIVE = "ENROLL_LIVE"
        const val SOURCE_ENROLL_PHOTO = "ENROLL_PHOTO"
        const val SOURCE_AUTO_ENRICH = "AUTO_ENRICH"

    }
}
