package com.example.data.local.face

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

/**
 * Base dediee aux empreintes faciales.
 *
 * Elle vit dans `noBackupFilesDir` : jamais sauvegardee dans le cloud, jamais transferee vers
 * un autre appareil, jamais incluse dans un export. Elle est volontairement separee de la base
 * principale du club.
 */
@Database(entities = [FaceTemplateEntity::class], version = 1, exportSchema = false)
abstract class FaceDatabase : RoomDatabase() {

    abstract fun faceTemplateDao(): FaceTemplateDao

    companion object {
        @Volatile
        private var INSTANCE: FaceDatabase? = null

        fun getInstance(context: Context): FaceDatabase {
            return INSTANCE ?: synchronized(this) {
                val dir = File(context.applicationContext.noBackupFilesDir, "face_db").apply { mkdirs() }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FaceDatabase::class.java,
                    File(dir, "face.db").absolutePath
                )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
