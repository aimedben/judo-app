package com.example.data.model

/**
 * Mode de saisie d'une seance de pointage.
 *
 * [CAMERA_QR] et [DOUCHETTE] existent depuis toujours ; [FACE] est le mode biometrique.
 * L'ancien booleen `isExternalScannerMode` reste lu pour compatibilite (`true` => DOUCHETTE).
 */
enum class PointageMode { CAMERA_QR, DOUCHETTE, FACE }

/**
 * Seuils de decision de la reconnaissance faciale.
 * Calibres pour EdgeFace-S : le pointage reste confirme par le coach (aucune inscription
 * automatique), on privilegie donc le rappel (moins de visages non retrouves) sur la
 * precision : le coach recadre les rares faux rapprochements.
 */
data class FaceRecognitionConfig(
    // Calibres de capture : enrôlement strict (5 echantillons frontal 112x112)
    // + lissage temporel de 3 frames -> le score vrai atteint ~0.55-0.70 en salle.
    // Le coach confirme chaque rapprochement : on abaisse les seuils pour privileger
    // le rappel (visages "non reconnus") sans jamais rien pointer automatiquement.
    val seuilHaut: Float = 0.48f,
    val seuilBas: Float = 0.36f,
    val margeMin: Float = 0.03f,
    val nFrames: Int = 1,
    val cooldownSeconds: Int = 60,
    val autoCheckInEnabled: Boolean = false
)

data class FaceCandidate(val memberId: Long, val score: Float)

/** Resultat de la machine a etats de reconnaissance. */
sealed class FaceDecision {
    data class Certain(val memberId: Long, val score: Float) : FaceDecision()
    data class Uncertain(val candidates: List<FaceCandidate>) : FaceDecision()
    object Unknown : FaceDecision()
}

/** Etat du moteur d'embedding sur cet appareil (modele .tflite + runtime). */
data class FaceEngineStatus(
    val ready: Boolean,
    val reason: String
)
