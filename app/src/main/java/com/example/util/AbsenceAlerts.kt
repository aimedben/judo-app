package com.example.util

import com.example.data.model.AttendanceWithMember
import com.example.data.model.Member
import com.example.data.model.ScheduledTrainingSession
import com.example.data.model.SessionStatus
import java.util.Calendar
import java.util.Locale

/**
 * Calculateur d'absences reelles d'un athlete : MEME DEFINITION que l'agenda de la
 * fiche (MemberAttendanceCalendar) pour rester toujours coherent avec les chiffres
 * affiches sur la fiche. Un jour compte comme absence si le jour est passe (ou
 * aujourd'hui), dans la periode de pointage officiel, que c'etait un jour
 * d'entrainement prevu (planning admin > seances) ou qu'une seance club a eu lieu,
 * et que l'athlete n'a AUCUN pointage (row reelle ou synthetisee) ce jour-la.
 */
object AbsenceAlerts {

    data class Alert(
        val member: Member,
        val absencesMonth: Int
    )

    private val dayOfWeekByFrenchName = mapOf(
        "dimanche" to Calendar.SUNDAY,
        "lundi" to Calendar.MONDAY,
        "mardi" to Calendar.TUESDAY,
        "mercredi" to Calendar.WEDNESDAY,
        "jeudi" to Calendar.THURSDAY,
        "vendredi" to Calendar.FRIDAY,
        "samedi" to Calendar.SATURDAY
    )

    /** Jours [Calendar] d'entrainement prevus pour ce membre (planning de sa categorie). */
    private fun memberTrainingDayOfWeeks(
        member: Member,
        scheduledSessions: List<ScheduledTrainingSession>
    ): Set<Int> = scheduledSessions
        .filter { it.status != SessionStatus.CANCELLED }
        .filter { s ->
            GroupCodes.matches(s.groupCode, member.groupCode) ||
                s.categoryName.equals(member.group.name, ignoreCase = true)
        }
        .distinctBy { "${it.dayOfWeekName}|${it.startTime}".lowercase() }
        .mapNotNull { it.dayOfWeekName.trim().lowercase(Locale.FRENCH).let { n -> dayOfWeekByFrenchName[n] } }
        .toSet()

    private fun dateString(year: Int, month0: Int, day: Int): String =
        String.format(Locale.US, "%04d-%02d-%02d", year, month0 + 1, day)

    fun monthLabel(year: Int, month0: Int): String =
        String.format(Locale.US, "%04d-%02d", year, month0 + 1)

    /**
     * Athletes dont le nombre d'absences du mois [month0] ([year]) depasse
     * [threshold], tries du plus grand nombre au plus petit.
     */
    fun membersWithMonthAbsences(
        members: List<Member>,
        attendances: List<AttendanceWithMember>,
        scheduledSessions: List<ScheduledTrainingSession>,
        officialStartDate: String,
        todayMillis: Long,
        year: Int,
        month0: Int,
        threshold: Int = 3
    ): List<Alert> {
        val attendedByMember = attendances.groupBy { it.memberId }
            .mapValues { (_, rows) -> rows.map { it.dateString }.toHashSet() }
        val clubSessionDays = attendances.map { it.dateString }.toHashSet()

        val todayCal = Calendar.getInstance().apply { timeInMillis = todayMillis }
        val todayStr = String.format(
            Locale.US,
            "%04d-%02d-%02d",
            todayCal.get(Calendar.YEAR),
            todayCal.get(Calendar.MONTH) + 1,
            todayCal.get(Calendar.DAY_OF_MONTH)
        )
        val maxDays = Calendar.getInstance().apply {
            clear()
            set(year, month0, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)

        val alerts = mutableListOf<Alert>()
        for (member in members) {
            if (member.status != "ACTIF") continue
            val attends = attendedByMember[member.id] ?: emptySet()
            val slotDays = memberTrainingDayOfWeeks(member, scheduledSessions)
            var count = 0
            for (day in 1..maxDays) {
                val dateStr = dateString(year, month0, day)
                if (dateStr > todayStr) break
                val dayCal = Calendar.getInstance().apply {
                    clear()
                    set(year, month0, day)
                }
                val dow = dayCal.get(Calendar.DAY_OF_WEEK)
                val isTrainingDay = dow in slotDays ||
                    clubSessionDays.contains(dateStr) ||
                    attends.contains(dateStr)
                if (!isTrainingDay) continue
                if (!OfficialPeriod.isOfficial(dateStr, officialStartDate)) continue
                if (attends.contains(dateStr)) continue
                count++
            }
            if (count >= threshold) alerts.add(Alert(member, count))
        }
        return alerts.sortedByDescending { it.absencesMonth }
    }
}
