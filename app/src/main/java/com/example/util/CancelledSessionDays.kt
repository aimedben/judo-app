package com.example.util

import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Journal des jours de séance ANNULÉS, partagé entre toutes les tablettes via la
 * sauvegarde `club_settings` de Supabase (colonne `cancelled_session_days`).
 *
 * Une séance annulée ne doit JAMAIS compter comme absence pour les athlètes de sa
 * catégorie : chaque entrée verrouille un couple (code de groupe, date) et les
 * calculateurs d'absence ([com.example.util.AbsenceAlerts], agenda de la fiche)
 * sautent ces dates-là.
 *
 * Format de stockage : `["U13|2026-11-07", "U15|2026-11-10", ...]`.
 */
object CancelledSessionDays {

    const val PREF_KEY = "cancelled_session_days_json"

    data class Day(
        val groupCode: String,
        val dateString: String
    ) {
        val key: String get() = "${groupCode.uppercase()}|$dateString"
    }

    fun parse(json: String?): List<Day> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length())
                .mapNotNull { arr.optString(it) }
                .filter { it.contains("|") }
                .map { entry ->
                    val group = entry.substringBefore("|").trim().uppercase()
                    val date = entry.substringAfter("|").trim()
                    if (group.isBlank() || !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date)) {
                        null
                    } else {
                        Day(group, date)
                    }
                }
                .filterNotNull()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun serialize(days: List<Day>): String {
        val arr = JSONArray()
        days.distinctBy { it.key }.forEach { arr.put(it.key) }
        return arr.toString()
    }

    fun load(appPrefs: SharedPreferences): List<Day> =
        parse(appPrefs.getString(PREF_KEY, null)).distinctBy { it.key }

    fun save(appPrefs: SharedPreferences, days: List<Day>) {
        appPrefs.edit().putString(PREF_KEY, serialize(days)).apply()
    }

    /** Fusionne les listes locale et cloud : une annulation est un fait historique. */
    fun merge(a: List<Day>, b: List<Day>): List<Day> =
        (a + b).distinctBy { it.key }
}
