package com.example.data.model

import com.example.util.GroupCodes

data class JudoCategory(
    val id: String,
    val name: String,
    val groupCode: String,
    val coach1Name: String,
    val coach2Name: String,
    val description: String = "",
    val isActive: Boolean = true
) {
    /** Codes couverts, un par entree : "U18,SENIOR" -> ["U18", "SENIOR"]. */
    val groupCodes: List<String>
        get() = GroupCodes.split(groupCode)

    /** Vrai si un athlete du groupe [memberGroupCode] appartient a cette categorie. */
    fun covers(memberGroupCode: String?): Boolean = GroupCodes.matches(groupCode, memberGroupCode)

    /** Libelle lisible des groupes couverts (ex. « Adults (U18) »). */
    val groupLabel: String
        get() = GroupCodes.label(groupCode)
}

enum class UserRole {
    COACH,
    ADMIN
}

data class ActiveTrainingSession(
    val categoryId: String,
    val categoryName: String,
    val startTime: String,
    val startDate: String,
    val coach1Name: String,
    val coach1Present: Boolean,
    val coach2Name: String,
    val coach2Present: Boolean,
    val isExternalScannerMode: Boolean = true,
    /** Mode de pointage de la seance. `isExternalScannerMode` reste lu pour compatibilite. */
    val pointageMode: PointageMode =
        if (isExternalScannerMode) PointageMode.DOUCHETTE else PointageMode.CAMERA_QR,
    /** Wall-clock timestamp when this session was created. Used to isolate the
     *  pointage of a new session from any earlier check-in made the same day. */
    val startTimestamp: Long = System.currentTimeMillis()
)

data class PastTrainingSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val categoryId: String,
    val categoryName: String,
    val groupCode: String,
    val dateString: String,
    val startTime: String,
    val endTime: String,
    val coach1Name: String,
    val coach1Present: Boolean,
    val coach2Name: String,
    val coach2Present: Boolean,
    val presentCount: Int,
    val absentCount: Int,
    val presentMemberNames: List<String> = emptyList(),
    val absentMemberNames: List<String> = emptyList()
) {
    /**
     * Les listes de noms sont la source de vérité : un compteur plus grand que la liste
     * déclarerait des judokas "présents" qui n'apparaissent nulle part dans l'écran.
     */
    val effectivePresentCount: Int
        get() = presentMemberNames.size

    val effectiveAbsentCount: Int
        get() = absentMemberNames.size
}

enum class CoachTab(val label: String) {
    SESSION("Séance"),
    ATHLETES("Athlètes"),
    HISTORY("Historique"),
    PROFILE("Profil")
}

sealed class CoachScanFeedback {
    data class Success(
        val member: Member,
        val isCotisationUpToDate: Boolean,
        val timeString: String
    ) : CoachScanFeedback()

    data class AlreadyChecked(
        val member: Member,
        val timeString: String
    ) : CoachScanFeedback()

    data class Invalid(
        val message: String
    ) : CoachScanFeedback()

    /**
     * Athlete reconnu mais rattache a une autre categorie que celle de la seance :
     * le pointage est refuse et le coach doit voir une alerte explicite.
     */
    data class WrongCategory(
        val member: Member,
        val memberGroupLabel: String,
        val sessionCategoryName: String
    ) : CoachScanFeedback()
}

