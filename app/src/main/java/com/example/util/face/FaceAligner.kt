package com.example.util.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Alignement ArcFace 112x112 a partir des 5 reperes ML Kit.
 *
 * Reperes attendus (ordre du gabarit, gauche/droite = cote IMAGE) :
 *   0 oeil gauche image, 1 oeil droit image, 2 base du nez,
 *   3 coin gauche de la bouche, 4 coin droit de la bouche.
 *
 * Le gabarit de reference est celui d'ArcFace (112x112) :
 *   (38.2946, 51.6963) (73.5318, 51.5014) (56.0252, 71.7366)
 *   (41.5493, 92.3655) (70.7299, 92.2041)
 *
 * La camera frontale renvoie des frames NON miroir. L'apercu affiche peut etre miroir : il ne
 * faut jamais aligner la frame miroir. Si une image miroir est fournie, `mirrored = true`
 * remet les points dans le repere de l'image avant le calcul (l'ordre des yeux est alors
 * inversee puis retabli par tri sur X, ce qui evite toute confusion gauche/droite).
 *
 * Normalisation d'entree (identique a l'entrainement EdgeFace) :
 *   RGB, redimensionnement bilineaire 112x112, puis (valeur - 127.5) / 128, planaire NCHW.
 */
object FaceAligner {

    const val OUTPUT_SIZE = 112

    val ARCFACE_TEMPLATE: List<FacePoint> = listOf(
        FacePoint(38.2946f, 51.6963f),
        FacePoint(73.5318f, 51.5014f),
        FacePoint(56.0252f, 71.7366f),
        FacePoint(41.5493f, 92.3655f),
        FacePoint(70.7299f, 92.2041f)
    )

    /** Erreur max toleree (px dans le repere 112x112) : au-dela, le visage est mal detecte. */
    private const val MAX_RESIDUAL_PX = 4.0f

    /**
     * Ordonne les reperes selon le gabarit.
     *
     * @param eyeLeft oeil "gauche" selon ML Kit (peut etre a droite a l'image)
     * @param eyeRight oeil "droit" selon ML Kit
     * @param nose base du nez
     * @param mouthLeft coin gauche de bouche selon ML Kit
     * @param mouthRight coin droit de bouche selon ML Kit
     * @param imageWidth largeur de l'image analysee (pour le miroir)
     * @param mirrored true si l'image fournie est deja miroir (apercu frontal)
     */
    fun orderLandmarks(
        eyeLeft: FacePoint,
        eyeRight: FacePoint,
        nose: FacePoint,
        mouthLeft: FacePoint,
        mouthRight: FacePoint,
        imageWidth: Int,
        mirrored: Boolean = false
    ): List<FacePoint>? {
        fun unmirror(p: FacePoint) = if (mirrored) FacePoint(imageWidth - p.x, p.y) else p

        val eyes = listOf(unmirror(eyeLeft), unmirror(eyeRight)).sortedBy { it.x }
        val mouth = listOf(unmirror(mouthLeft), unmirror(mouthRight)).sortedBy { it.x }

        // Sanity check : les deux yeux doivent etre nettement separes horizontalement,
        // la bouche sous les yeux, le nez entre les deux.
        val eyeDx = eyes[1].x - eyes[0].x
        if (eyeDx < 8f) return null
        if (mouth[0].y <= eyes[0].y + 4f) return null
        if (nose.y <= eyes[0].y || nose.y >= mouth[0].y + 8f) return null

        val ordered = listOf(eyes[0], eyes[1], nose, mouth[0], mouth[1])
        if (ordered.any { it.x.isNaN() || it.y.isNaN() }) return null
        return ordered
    }

    /** Transforme les points image -> gabarit 112x112, ou null si la geometrie est invalide. */
    fun computeTransform(landmarks: List<FacePoint>): SimilarityTransform? {
        if (landmarks.size != 5) return null
        val transform = SimilarityTransform.solve(landmarks, ARCFACE_TEMPLATE) ?: return null
        val residual = transform.maxError(landmarks.zip(ARCFACE_TEMPLATE))
        return transform.takeIf { residual <= MAX_RESIDUAL_PX }
    }

    /** Recadre/redresse le visage en 112x112. L'image source n'est pas modifiee. */
    fun align(bitmap: Bitmap, landmarks: List<FacePoint>): Bitmap? {
        val transform = computeTransform(landmarks) ?: return null
        return try {
            val output = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
            val matrix = Matrix().apply {
                setValues(
                    floatArrayOf(
                        transform.a, -transform.b, transform.tx,
                        transform.b, transform.a, transform.ty,
                        0f, 0f, 1f
                    )
                )
            }
            val canvas = Canvas(output)
            canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            output
        } catch (_: Exception) {
            null
        }
    }
}
