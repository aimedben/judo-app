package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Date de debut du pointage officiel (reglage du club, espace Admin > Parametres).
 *
 * Tout ce qui precede cette date est ignore dans les statistiques (presences, absences,
 * taux), sans jamais supprimer de donnees : l'historique reste consultable en base.
 *
 * Les dates sont stockees au format ISO `yyyy-MM-dd`, donc la comparaison de chaines
 * est aussi une comparaison chronologique (pas de fuseau horaire, pas de timestamp).
 */
object OfficialPeriod {

    /** Date du jour au format ISO (yyyy-MM-dd). */
    fun todayIsoDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Vrai si [dateString] est dans la periode officielle (vide = aucune restriction). */
    fun isOfficial(dateString: String?, startDate: String?): Boolean {
        val start = startDate?.trim()?.take(10).orEmpty()
        if (start.isEmpty()) return true
        val day = dateString?.trim()?.take(10).orEmpty()
        if (day.isEmpty()) return true
        return day >= start
    }
}
