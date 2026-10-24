package com.example.data.local.face

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Empreinte faciale locale d'un athlete.
 *
 * `embedding` contient TOUJOURS des octets chiffres (AES-GCM 256) : aucune photo de visage
 * n'est stockee, et cette table ne doit jamais apparaitre dans un DTO, un export ou un
 * payload de synchronisation.
 */
@Entity(
    tableName = "face_template",
    indices = [Index("memberId")]
)
data class FaceTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val memberId: Long,
    val embedding: ByteArray,
    val iv: ByteArray,
    val modelVersion: String,
    val qualityScore: Float,
    val createdAt: Long,
    val source: String
)
