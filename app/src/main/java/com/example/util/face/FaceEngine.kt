package com.example.util.face

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.data.model.FaceEngineStatus

/**
 * Etat et cycle de vie du moteur EdgeFace (ONNX Runtime Mobile, 100 % local).
 *
 * Le modele n'est charge qu'une fois par processus. Le hash du fichier de poids est verifie
 * au chargement ; un modele modifie est refuse (les empreintes existantes ne sont plus
 * comparables).
 */
object FaceEngine {

    @Volatile
    private var cached: FaceEmbedder? = null

    @Volatile
    private var loadAttempted = false

    @Volatile
    private var hashValidated: Boolean? = null

    /** Verifie le hash des poids une seule fois par processus. */
    private fun hashValid(context: Context): Boolean {
        return hashValidated ?: synchronized(this) {
            hashValidated ?: EdgeFaceOnnxEmbedder.verifyAsset(context).also { hashValidated = it }
        }
    }

    fun embedder(context: Context): FaceEmbedder? {
        cached?.let { return it }
        if (loadAttempted) return cached
        synchronized(this) {
            if (!loadAttempted) {
                cached = EdgeFaceOnnxEmbedder.create(context.applicationContext)
                loadAttempted = true
            }
            return cached
        }
    }

    fun resetForTests() {
        synchronized(this) {
            runCatching { cached?.close() }
            cached = null
            loadAttempted = false
        }
    }

    /**
     * Moteur utilisable pour l'enrolement, sans exiger la camera ici :
     * asset present, runtime lie et hash valide.
     */
    fun engineReady(context: Context): Boolean {
        val modelPresent = runCatching {
            context.assets.open(EdgeFaceOnnxEmbedder.MODEL_ASSET).use { true }
        }.getOrDefault(false)
        return modelPresent && runtimeAvailable() && hashValid(context)
    }

    /**
     * "Actif" exige les trois conditions : modele valide, permission camera accordee et
     * galerie du groupe non vide. Sinon le message explique precisement ce qui manque.
     */
    fun status(
        context: Context,
        cameraGranted: Boolean,
        gallerySize: Int
    ): FaceEngineStatus {
        val modelPresent = runCatching {
            context.assets.open(EdgeFaceOnnxEmbedder.MODEL_ASSET).use { true }
        }.getOrDefault(false)

        return when {
            !modelPresent -> FaceEngineStatus(
                ready = false,
                reason = "Modèle de reconnaissance non installé (${EdgeFaceOnnxEmbedder.MODEL_ASSET} manquant)."
            )
            !runtimeAvailable() -> FaceEngineStatus(
                ready = false,
                reason = "Moteur d'empreinte non lié (ONNX Runtime Mobile absent du build)."
            )
            !hashValid(context) -> FaceEngineStatus(
                ready = false,
                reason = "Modèle de reconnaissance invalide (hash du fichier de poids incorrect)."
            )
            !cameraGranted && !hasCameraPermission(context) -> FaceEngineStatus(
                ready = false,
                reason = "Autorisez la caméra pour utiliser la reconnaissance faciale."
            )
            gallerySize <= 0 -> FaceEngineStatus(
                ready = false,
                reason = "Aucun visage enrôlé dans ce groupe : la reconnaissance reste inactive."
            )
            else -> FaceEngineStatus(
                ready = true,
                reason = "Reconnaissance EdgeFace active — la présence est toujours confirmée par le coach."
            )
        }
    }

    private fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun runtimeAvailable(): Boolean = runCatching {
        Class.forName("ai.onnxruntime.OrtEnvironment")
        true
    }.getOrDefault(false)
}
