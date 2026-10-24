package com.example.util.face

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face

/**
 * Controle qualite avant embedding. Un visage flou, trop petit, trop incline ou de profil
 * n'est jamais envoye au modele : cela evite de polluer la galerie et de produire des scores
 * trompeurs.
 */
object FaceQuality {

    const val MIN_FACE_RATIO = 0.12f
    const val MAX_YAW_DEGREES = 35f
    const val MAX_ROLL_DEGREES = 25f
    const val MIN_SHARPNESS = 28f

    // Niveau STRICT (enrôlement uniquement) : les empreintes de la galerie doivent etre
    // nettes, grandes et de face — c'est la base de tout rappel correct au pointage.
    const val MIN_FACE_RATIO_STRICT = 0.20f
    const val MAX_YAW_DEGREES_STRICT = 15f
    const val MAX_ROLL_DEGREES_STRICT = 12f
    const val MIN_SHARPNESS_STRICT = 50f

    data class Verdict(val acceptable: Boolean, val hint: String?, val sharpness: Float)

    /**
     * @param strict true pour l'enrôlement (gabarits de meilleure qualite, exigeants)
     *               ; false pour le pointage temps reel (tolerant, lisible en salle).
     */
    fun check(
        face: Face,
        frameWidth: Int,
        frameHeight: Int,
        aligned: Bitmap?,
        strict: Boolean = false
    ): Verdict {
        val minRatio = if (strict) MIN_FACE_RATIO_STRICT else MIN_FACE_RATIO
        val maxYaw = if (strict) MAX_YAW_DEGREES_STRICT else MAX_YAW_DEGREES
        val maxRoll = if (strict) MAX_ROLL_DEGREES_STRICT else MAX_ROLL_DEGREES
        val minSharpness = if (strict) MIN_SHARPNESS_STRICT else MIN_SHARPNESS

        if (frameWidth <= 0 || frameHeight <= 0) {
            return Verdict(false, "Image invalide", 0f)
        }
        val ratio = face.boundingBox.width().toFloat() / frameWidth.toFloat()
        if (ratio < minRatio) {
            return Verdict(false, "Rapprochez-vous de la caméra", 0f)
        }
        if (kotlin.math.abs(face.headEulerAngleY) > maxYaw) {
            return Verdict(false, "Regardez la caméra de face", 0f)
        }
        if (kotlin.math.abs(face.headEulerAngleZ) > maxRoll) {
            return Verdict(false, "Gardez la tête droite", 0f)
        }
        val sharpness = aligned?.let { laplacianVariance(it) } ?: 0f
        if (sharpness < minSharpness) {
            return Verdict(false, "Image trop floue, restez immobile", sharpness)
        }
        return Verdict(true, null, sharpness)
    }

    /** Variance du laplacien sur une version grise : mesure classique de nettete. */
    fun laplacianVariance(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 3 || h < 3) return 0f
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = ((p shr 16 and 0xFF) * 0.299f + (p shr 8 and 0xFF) * 0.587f + (p and 0xFF) * 0.114f)
        }
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val center = gray[y * w + x]
                val lap = gray[(y - 1) * w + x] + gray[(y + 1) * w + x] +
                    gray[y * w + x - 1] + gray[y * w + x + 1] - 4 * center
                sum += lap
                sumSq += lap * lap
                n++
            }
        }
        if (n == 0) return 0f
        val mean = sum / n
        return (sumSq / n - mean * mean).toFloat()
    }
}
