package com.example.util.face

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.Executors

/** Un echantillon valide : embedding (L2) + score de nettete. Aucune image conservée. */
data class FaceCapture(val embedding: FloatArray, val quality: Float)

/**
 * Analyseur d'enrolement : meme detection/alignement/qualite que le pointage, sans galerie ni
 * decision. Aucune frame n'est ecrite sur disque, aucun log d'embedding.
 */
class FaceCaptureAnalyzer(
    private val embedder: FaceEmbedder,
    private val onCapture: (FaceCapture) -> Unit,
    private val onHint: (String) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val minIntervalMs = (1000L / TARGET_FPS)
    private var lastAnalyzedAt = 0L

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(FaceQuality.MIN_FACE_RATIO)
            .build()
    )

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalyzedAt < minIntervalMs) {
            image.close()
            return
        }
        lastAnalyzedAt = now

        val media = image.image
        if (media == null) {
            image.close()
            return
        }

        // Screening leger sans bitmap : taille + pose avant de convertir la frame.
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        detector.process(input)
            .addOnSuccessListener(executor) { faces ->
                try {
                    handle(faces, image)
                } finally {
                    image.close()
                }
            }
            .addOnFailureListener(executor) {
                mainHandler.post { onHint("Caméra indisponible") }
                image.close()
            }
    }

    private fun handle(faces: List<Face>, image: ImageProxy) {
        val bitmap = image.toUprightBitmap() ?: return

        val face = faces.maxByOrNull {
            it.boundingBox.width().toLong() * it.boundingBox.height().toLong()
        } ?: run {
            bitmap.recycle()
            mainHandler.post { onHint("Rapprochez le visage du cadre") }
            return
        }

        val landmarks = landmarksOf(face, bitmap.width) ?: run {
            bitmap.recycle()
            mainHandler.post { onHint("Visage incomplet : regardez la caméra") }
            return
        }
        val aligned = FaceAligner.align(bitmap, landmarks)
        if (aligned == null) {
            bitmap.recycle()
            mainHandler.post { onHint("Repositionnez le visage dans le cadre") }
            return
        }
        // Enrôlement : gabarits STRICTS (face, grands, nets) pour une galerie fiable.
        val verdict = FaceQuality.check(face, bitmap.width, bitmap.height, aligned, strict = true)
        bitmap.recycle()
        if (!verdict.acceptable) {
            aligned.recycle()
            mainHandler.post { onHint(verdict.hint ?: "") }
            return
        }
        val embedding = embedder.embed(aligned)
        aligned.recycle()
        if (embedding == null) {
            mainHandler.post { onHint("Moteur indisponible, réessayez") }
            return
        }
        mainHandler.post { onCapture(FaceCapture(embedding, verdict.sharpness)) }
    }

    private fun landmarksOf(face: Face, imageWidth: Int): List<FacePoint>? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return null
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return null
        return FaceAligner.orderLandmarks(
            eyeLeft = FacePoint(leftEye.x, leftEye.y),
            eyeRight = FacePoint(rightEye.x, rightEye.y),
            nose = FacePoint(nose.x, nose.y),
            mouthLeft = FacePoint(mouthLeft.x, mouthLeft.y),
            mouthRight = FacePoint(mouthRight.x, mouthRight.y),
            imageWidth = imageWidth,
            mirrored = false
        )
    }

    override fun close() {
        runCatching { detector.close() }
        executor.shutdown()
    }

    companion object {
        const val TARGET_FPS = 6
    }
}
