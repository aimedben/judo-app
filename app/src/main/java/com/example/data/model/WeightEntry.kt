package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Une mesure de poids pour un athlète (ou un coach).
 * - [subjectType] : "ATHLETE" ou "COACH"
 * - [subjectId]   : id local du sujet (Member.id ou Coach.id)
 * - [dateString]  : jour de la mesure (yyyy-MM-dd)
 * - [weightKg]    : poids en kilogrammes
 */
@Entity(
    tableName = "weight_entries",
    indices = [Index(value = ["subjectType", "subjectId", "dateString"], unique = true)]
)
data class WeightEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectType: String = "ATHLETE",
    val subjectId: Long,
    val dateString: String,
    val weightKg: Float,
    val timestamp: Long = System.currentTimeMillis()
)