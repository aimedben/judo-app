package com.example.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sauvegarde locale "temps reel" de toute la base dans un fichier nomme `jcsdb`.
 *
 * Ecrit un instantane JSON apres chaque modification (membre, presence, categorie,
 * planning, historique) : meme sans reseau ni Supabase, les donnees du club vivent
 * dans un fichier lisible et recuperable. L'ecriture est atomique (fichier temporaire
 * puis renommage) pour ne jamais laisser un `jcsdb` tronque.
 */
object LocalSnapshotStore {

    const val FILE_NAME = "jcsdb"

    private val writeMutex = Mutex()

    /** Emplacement du fichier : stockage de l'app (Android/data/.../files/jcsdb). */
    fun file(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, FILE_NAME)
    }

    suspend fun write(context: Context, json: String): Boolean = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val target = file(context)
            val tmp = File(target.parentFile, "$FILE_NAME.tmp")
            try {
                target.parentFile?.mkdirs()
                tmp.writeText(json)
                if (target.exists() && !target.delete()) {
                    tmp.delete()
                    return@withLock false
                }
                if (!tmp.renameTo(target)) {
                    target.writeText(json)
                    tmp.delete()
                }
                true
            } catch (_: Exception) {
                runCatching { tmp.delete() }
                false
            }
        }
    }

    fun exists(context: Context): Boolean = file(context).let { it.exists() && it.length() > 0 }

    fun sizeBytes(context: Context): Long = file(context).let { if (it.exists()) it.length() else 0L }
}
