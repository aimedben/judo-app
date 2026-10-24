package com.example.util.face

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.security.MessageDigest

/**
 * Implementation EdgeFace-S (Idiap, variante gamma 0.5) via ONNX Runtime Mobile.
 *
 * Le modele est execute LOCALEMENT : aucune image, aucun embedding ne sort de l'appareil.
 * Les dimensions d'entree/sortie sont lues depuis le modele lui-meme (jamais codees en dur),
 * et `modelVersion` embarque la dimension de sortie + le hash du fichier de poids : un modele
 * different invalide automatiquement les empreintes existantes (re-enrolement requis).
 *
 * Licence des poids : CC BY-NC-SA 4.0 (Idiap). Voir NOTICE-EDGEFACE.md a la racine du depot.
 */
class EdgeFaceOnnxEmbedder private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    override val modelVersion: String,
    override val inputWidth: Int,
    override val inputHeight: Int,
    override val dimensions: Int
) : FaceEmbedder {

    override val isReady: Boolean = true

    override fun embed(alignedFace: Bitmap): FloatArray? {
        return try {
            val scaled = if (alignedFace.width != inputWidth || alignedFace.height != inputHeight) {
                Bitmap.createScaledBitmap(alignedFace, inputWidth, inputHeight, true)
            } else {
                alignedFace
            }

            val pixels = IntArray(inputWidth * inputHeight)
            scaled.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
            if (scaled !== alignedFace) scaled.recycle()

            val plane = inputWidth * inputHeight
            val input = FloatArray(3 * plane)
            for (i in 0 until plane) {
                val pixel = pixels[i]
                // EdgeFace a ete entraine avec une normalisation (valeur - 127.5) / 128 en RGB.
                input[i] = (((pixel shr 16) and 0xFF) - 127.5f) / 128f
                input[plane + i] = (((pixel shr 8) and 0xFF) - 127.5f) / 128f
                input[2 * plane + i] = ((pixel and 0xFF) - 127.5f) / 128f
            }

            val shape = longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
            OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
                session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                    val output = result.get(0) as? OnnxTensor ?: return null
                    val buffer = output.floatBuffer
                    val vector = FloatArray(buffer.remaining())
                    buffer.get(vector)
                    FaceMath.l2Normalize(vector)
                }
            }
        } catch (e: Exception) {
            // Jamais de log de vecteur ni d'image : uniquement le type d'erreur.
            Log.w("FaceEmbedder", "Inference EdgeFace impossible: ${e.javaClass.simpleName}")
            null
        }
    }

    override fun close() {
        runCatching { session.close() }
    }

    companion object {
        const val MODEL_ASSET = "edgeface_s.onnx"
        const val MODEL_LABEL = "edgeface-s-gamma05"

        /** Hash du fichier de poids livre (SHA-256), verifie a chaque chargement. */
        const val EXPECTED_SHA256 =
            "B850767CF791BDA585600B5C4C7D7432B2F998CCD862CAAE34EF1AFA967D2E54"

        /** Verifie le hash du fichier sans creer de session (utilisable depuis l'UI). */
        fun verifyAsset(context: Context): Boolean = runCatching {
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            sha256(bytes).equals(EXPECTED_SHA256, ignoreCase = true)
        }.getOrDefault(false)

        fun create(context: Context): EdgeFaceOnnxEmbedder? {
            return try {
                val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
                val hash = sha256(bytes)
                if (!hash.equals(EXPECTED_SHA256, ignoreCase = true)) {
                    Log.e("FaceEmbedder", "Hash du modele inattendu: modele ignore")
                    return null
                }

                val env = OrtEnvironment.getEnvironment()
                val session = env.createSession(bytes, OrtSession.SessionOptions())
                val inputInfo = session.inputInfo.values.first().info as? ai.onnxruntime.TensorInfo
                    ?: return null
                val outputInfo = session.outputInfo.values.first().info as? ai.onnxruntime.TensorInfo
                    ?: return null

                val inputShape = inputInfo.shape
                val outputShape = outputInfo.shape
                val height = inputShape.getOrNull(2)?.toInt() ?: 112
                val width = inputShape.getOrNull(3)?.toInt() ?: 112
                val dims = outputShape.lastOrNull()?.toInt() ?: 512

                EdgeFaceOnnxEmbedder(
                    env = env,
                    session = session,
                    modelVersion = "$MODEL_LABEL-${width}x$height-$dims-${hash.take(8)}",
                    inputWidth = width,
                    inputHeight = height,
                    dimensions = dims
                )
            } catch (e: Throwable) {
                Log.w("FaceEmbedder", "Chargement EdgeFace impossible: ${e.javaClass.simpleName}")
                null
            }
        }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02X".format(it) }
    }
}
