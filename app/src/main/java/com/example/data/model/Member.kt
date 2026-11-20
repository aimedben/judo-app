package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "members",
    indices = [Index(value = ["qrCode"], unique = true)]
)
data class Member(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val qrCode: String,
    val firstName: String,
    val lastName: String,
    val birthDate: String, // e.g. "2012-05-14"
    val phone: String,
    val groupCode: String, // e.g. "U15"
    val beltName: String, // e.g. "VERTE"
    val status: String = "ACTIF", // "ACTIF", "SUSPENDU" or "BLESSE"
    val injuryStartDate: String = "", // yyyy-MM-dd
    val injuryEndDate: String = "", // yyyy-MM-dd (vide = blessure en cours)
    val registrationDate: String = "2026-09-01",
    val photoUrl: String = "",
    val notes: String = ""
) {
    val fullName: String
        get() = "$firstName $lastName"

    val belt: Belt
        get() = Belt.fromString(beltName)

    val group: JudoGroup
        get() = JudoGroup.fromString(groupCode)

    val isInjured: Boolean
        get() = status.equals("BLESSE", ignoreCase = true)

    val isActiveAthlete: Boolean
        get() = status.equals("ACTIF", ignoreCase = true)

    val isSuspended: Boolean
        get() = status.equals("SUSPENDU", ignoreCase = true)

    val isCotisationUpToDate: Boolean
        get() = status != "SUSPENDU" && !notes.contains("retard", ignoreCase = true)

    /** Vrai si [dateString] (yyyy-MM-dd) tombe dans la periode de blessure declaree. */
    fun isInjuredOn(dateString: String): Boolean {
        if (!isInjured) return false
        if (injuryStartDate.isBlank()) return true
        if (dateString < injuryStartDate) return false
        if (injuryEndDate.isNotBlank() && dateString > injuryEndDate) return false
        return true
    }
}
