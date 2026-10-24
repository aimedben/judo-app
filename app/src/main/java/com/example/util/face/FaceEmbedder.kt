package com.example.util.face

import android.graphics.Bitmap

/**
 * Contrat du moteur d'embedding facial.
 *
 * Remplacer le modele (EdgeFace-S -> autre) ne doit toucher qu'une implementation de cette
 * interface : la galerie, la decision et l'UI ne dependent d'aucun modele precis.
 */
interface FaceEmbedder : AutoCloseable {

    /** Identifiant versionne, incluant la dimension de sortie et le hash du fichier de poids. */
    val modelVersion: String

    val inputWidth: Int
    val inputHeight: Int
    val dimensions: Int

    val isReady: Boolean

    /**
     * Calcule l'embedding d'un visage DEJA aligne (format ArcFace 112x112).
     * Retourne null si le modele n'est pas disponible ou si l'inference echoue.
     * Aucune image n'est conservee.
     */
    fun embed(alignedFace: Bitmap): FloatArray?

    override fun close()
}
