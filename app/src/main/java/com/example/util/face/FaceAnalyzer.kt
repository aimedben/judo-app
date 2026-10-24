package com.example.util.face

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.data.model.FaceDecision
import com.example.data.model.FaceRecognitionConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.Executors

/** Resultat d'une frame analysee. Ne contient AUCUN embedding ni image hors snapshot valide. */
data class FaceObservation(
    val decision: FaceDecision,
    val hint: String?,
    val faceDetected: Boolean,
    val fps: Float,
    /**
     * Recadrage rectangulaire (4:3) centre sur le visage, produit UNIQUEMENT quand la
     * reconnaissance est validee ([FaceDecision.Certain]). Il sert a l'affichage 2 s dans
     * le cadre video ; la vue charge de le recycler apres usage.
     */
    val snapshot: Bitmap? = null
)

/**
 * Pipeline temps reel : ImageAnalysis -> ML Kit -> qualite -> alignement ArcFace 112x112
 * -> embedding EdgeFace -> cosinus contre la galerie du groupe -> FaceCheckInController.
 *
 * Regles :
 *  - 7 fps maximum, KEEP_ONLY_LATEST cote CameraX (configure dans le composant camera) ;
 *  - aucune frame ecrite sur disque, aucune image conservee apres analyse (bitmap recycle) ;
 *  - aucun log d'embedding, de score nominal ou d'identite ;
 *  - `autoCheckInEnabled = false` : une decision Certain NE POINTE RIEN ici ; l'UI hote
 *    orchestre le pointage via le pipeline metier existant (doublons, cotisation...).
 */
class FaceAnalyzer(
    private val embedder: FaceEmbedder,
    initialGallery: Map<Long, List<FloatArray>>,
    private val config: FaceRecognitionConfig = FaceRecognitionConfig(),
    private val onResult: (FaceObservation) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {

    @Volatile
    var gallery: Map<Long, List<FloatArray>> = sanitize(initialGallery)
        set(value) { field = sanitize(value) }

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(FaceQuality.MIN_FACE_RATIO)
            .enableTracking()
            .build()
    )

    private val controller = FaceCheckInController(config)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val minIntervalMs = (1000L / TARGET_FPS)
    private var lastAnalyzedAt = 0L
    private var frames = 0
    private var fpsWindowStart = 0L
    private var currentFps = 0f

    @Volatile
    private var closed = false

    // Lissage temporel : moyenne glissante des N derniers embeddings du MEME visage
    // suivi (trackId ML Kit). Reduit le bruit de pose/eclairage d'une frame isolee et
    // augmente la marge entre vrai et faux candidat. Uniquement des vecteurs 512-dim
    // en memoire, jamais d'image ; remis a zero aux changements de visage et close().
    private var emaTrackId: Int? = null
    private val emaWindow = ArrayList<FloatArray>(EMA_FRAMES)

    private fun smoothedEmbedding(trackId: Int, candidate: FloatArray): FloatArray {
        if (trackId != emaTrackId) {
            emaTrackId = trackId
            emaWindow.clear()
        }
        if (emaWindow.size >= EMA_FRAMES) emaWindow.removeAt(0)
        emaWindow.add(candidate)
        val sum = FloatArray(candidate.size)
        for (vector in emaWindow) {
            for (i in sum.indices) sum[i] += vector[i]
        }
        val inv = 1f / emaWindow.size
        for (i in sum.indices) sum[i] *= inv
        return FaceMath.l2Normalize(sum)
    }

    private fun sanitize(source: Map<Long, List<FloatArray>>): Map<Long, List<FloatArray>> =
        source.mapValues { (_, vectors) -> vectors.map { FaceMath.l2Normalize(it) } }

    override fun analyze(image: ImageProxy) {
        if (closed) {
            image.close()
            return
        }
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

        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        detector.process(input)
            .addOnSuccessListener(executor) { faces ->
                try {
                    handle(faces, image)
                } finally {
                    image.close()
                }
            }
            .addOnFailureListener(executor) { image.close() }
    }

    private fun handle(faces: List<Face>, image: ImageProxy) {
        val bitmap = image.toUprightBitmap() ?: return

        val face = faces.maxByOrNull { it.boundingBox.width().toLong() * it.boundingBox.height() }
        if (face == null) {
            bitmap.recycle()
            publish(FaceObservation(FaceDecision.Unknown, "Approchez votre visage", false, tick()))
            return
        }

        val landmarks = landmarksOf(face, bitmap.width)
        if (landmarks == null) {
            bitmap.recycle()
            publish(FaceObservation(FaceDecision.Unknown, "Visage incomplet : regardez la caméra", true, tick()))
            return
        }

        val aligned = FaceAligner.align(bitmap, landmarks)
        if (aligned == null) {
            bitmap.recycle()
            publish(FaceObservation(FaceDecision.Unknown, "Repositionnez votre visage", true, tick()))
            return
        }

        val verdict = FaceQuality.check(face, bitmap.width, bitmap.height, aligned)
        if (!verdict.acceptable) {
            bitmap.recycle()
            aligned.recycle()
            publish(FaceObservation(FaceDecision.Unknown, verdict.hint, true, tick()))
            return
        }

        val embedding = embedder.embed(aligned)
        aligned.recycle()

        if (embedding == null) {
            bitmap.recycle()
            publish(FaceObservation(FaceDecision.Unknown, "Reconnaissance indisponible", true, tick()))
            return
        }

        val trackId = face.trackingId ?: -1
        val rawScores = scoreGallery(embedding)
        val smoothed = smoothedEmbedding(trackId, embedding)
        val smoothScores = scoreGallery(smoothed)
        val scores = mergeScores(rawScores, smoothScores)
        val decision = controller.evaluate(trackId, scores)
        // Recadrage 4:3 centree sur le visage, produit UNIQUEMENT si le pointage est
        // valide (Certain). Jamais d'image avant une reconnaissance confirmee.
        val snapshot = if (decision is FaceDecision.Certain) {
            faceCrop(bitmap, face.boundingBox)
        } else {
            null
        }
        bitmap.recycle()
        val hint = when (decision) {
            is FaceDecision.Certain -> null
            is FaceDecision.Uncertain -> "Vérifiez : plusieurs visages proches"
            FaceDecision.Unknown -> if (scores.isEmpty()) "Aucun visage enrôlé dans ce groupe" else "Visage non reconnu"
        }
        publish(FaceObservation(decision, hint, true, tick(), snapshot))
    }

    /**
     * Recadrage horizontal 4:3 centre sur le visage detecte, avec marge autour de la
     * boite englobante. Retourne null si le recadrage est impossible (visage sur le bord).
     */
    private fun faceCrop(bitmap: Bitmap, box: Rect): Bitmap? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        val margin = 0.55f
        var w = (box.width() * (1f + margin * 2f)).toInt().coerceAtLeast(1)
        var h = (box.height() * (1f + margin * 2f)).toInt().coerceAtLeast(1)
        // Format 4:3 horizontal (borne de pointage) : on elargit la dimension la plus courte.
        if (w * 3 / 4f > h) h = (w * 3 / 4f).toInt() else w = (h * 4 / 3f).toInt()
        val cropW = minOf(w, bitmap.width)
        val cropH = minOf(h, bitmap.height)
        if (cropW <= 0 || cropH <= 0) return null
        val left = (box.exactCenterX() - cropW / 2f).toInt().coerceIn(0, bitmap.width - cropW)
        val top = (box.exactCenterY() - cropH / 2f).toInt().coerceIn(0, bitmap.height - cropH)
        val crop = runCatching { Bitmap.createBitmap(bitmap, left, top, cropW, cropH) }.getOrNull()
            ?: return null
        // Limite la memoire du snapshot affiche 2 s.
        val maxWidth = 720
        return if (crop.width > maxWidth) {
            val scaled = runCatching {
                Bitmap.createScaledBitmap(
                    crop,
                    maxWidth,
                    (crop.height * (maxWidth.toFloat() / crop.width)).toInt().coerceAtLeast(1),
                    true
                )
            }.getOrNull()
            if (scaled != null && scaled !== crop) crop.recycle()
            scaled
        } else {
            crop
        }
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

    /** Meilleur score par athlete entre la frame brute et la version lissee. */
    private fun mergeScores(a: Map<Long, Float>, b: Map<Long, Float>): Map<Long, Float> {
        if (b.isEmpty()) return a
        val merged = HashMap<Long, Float>(a.size + b.size)
        a.forEach { (id, score) -> merged[id] = score }
        b.forEach { (id, score) ->
            val known = merged[id]
            if (known == null || score > known) merged[id] = score
        }
        return merged
    }

    private fun scoreGallery(embedding: FloatArray): Map<Long, Float> {
        val snapshot = gallery
        if (snapshot.isEmpty()) return emptyMap()
        val scores = HashMap<Long, Float>(snapshot.size)
        snapshot.forEach { (memberId, templates) ->
            var best = -1f
            for (vector in templates) {
                val score = FaceMath.cosine(embedding, vector)
                if (score > best) best = score
            }
            if (best >= 0f) scores[memberId] = best
        }
        return scores
    }

    private fun tick(): Float {
        val now = SystemClock.elapsedRealtime()
        if (fpsWindowStart == 0L) fpsWindowStart = now
        frames++
        val elapsed = now - fpsWindowStart
        if (elapsed >= 1000L) {
            currentFps = frames * 1000f / elapsed.toFloat()
            frames = 0
            fpsWindowStart = now
        }
        return currentFps
    }

    private fun publish(observation: FaceObservation) {
        mainHandler.post { onResult(observation) }
    }

    fun reset() {
        controller.reset()
        lastAnalyzedAt = 0L
        frames = 0
        fpsWindowStart = 0L
        currentFps = 0f
        dropTrack()
    }

    /** Libere le lissage temporel du visage suivi en cours. */
    private fun dropTrack() {
        emaTrackId = null
        emaWindow.clear()
    }

    override fun close() {
        closed = true
        dropTrack()
        runCatching { detector.close() }
        executor.shutdown()
    }

    companion object {
        const val TARGET_FPS = 7

        /** Taille de la fenetre de lissage temporel (frames d'un meme track). */
        private const val EMA_FRAMES = 3
    }
}
