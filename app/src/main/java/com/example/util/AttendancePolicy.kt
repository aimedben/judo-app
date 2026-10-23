package com.example.util

/**
 * Regles de presence du club, isolees de tout dependance Android/Room afin d'etre
 * verifiables par des tests unitaires JVM.
 *
 * Principe non negociable : un athlete n'est PRESENT que s'il possede une ligne de
 * pointage enregistree pendant la seance courante. Sa simple presence dans la liste
 * des inscrits, la creation de sa fiche, l'association de sa carte ou la lecture de
 * ses informations ne valent jamais pointage.
 */
object AttendancePolicy {

    /**
     * Ids des athletes reellement pointes pendant la seance ayant demarre a
     * [sessionStartTimestamp].
     *
     * [attendances] : paires (memberId, timestamp) des pointages connus du jour.
     *
     * Un pointage anterieur au demarrage de la seance (donc issu d'une seance
     * precedente) est ignore : une nouvelle seance demarre toujours vide.
     */
    fun presentMemberIds(
        sessionStartTimestamp: Long,
        attendances: List<Pair<Long, Long>>
    ): Set<Long> = attendances.asSequence()
        .filter { it.second >= sessionStartTimestamp }
        .map { it.first }
        .toSet()

    /**
     * Compteurs d'une seance.
     *
     * Seuls les athletes attendus ([expectedMemberIds]) sont comptes : un pointage
     * provenant d'un athlete d'une autre categorie ne modifie jamais les totaux.
     * Absent = attendu mais non pointe.
     */
    fun totals(
        expectedMemberIds: Collection<Long>,
        presentMemberIds: Set<Long>
    ): PointageTotals {
        val expected = expectedMemberIds.toSet()
        val present = expected.count { it in presentMemberIds }
        val total = expected.size
        return PointageTotals(
            present = present,
            absent = (total - present).coerceAtLeast(0),
            total = total
        )
    }

    /**
     * Un athlete est present dans la seance s'il a ete pointe pendant celle-ci.
     * Formulation explicite utilisee par les listes de l'ecran de pointage.
     */
    fun isPresent(memberId: Long, presentMemberIds: Set<Long>): Boolean =
        memberId in presentMemberIds
}

/** Totaux de pointage d'une seance : presents + absents == attendus. */
data class PointageTotals(
    val present: Int,
    val absent: Int,
    val total: Int
)
