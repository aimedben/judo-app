package com.example.util

import com.example.data.model.JudoGroup

/**
 * Annees de naissance affichees sur une categorie.
 *
 * La colonne `description` d'une categorie ne contient plus une tranche d'age
 * ("10-11 ans") mais les annees concernees : "2017", "2018-2019",
 * "2020-2021-2022" ou "2012 et moins".
 */
object CategoryYears {

    const val MIN_YEAR = 2010
    const val MAX_YEAR = 2026
    const val UP_TO_SUFFIX = " et moins"
    const val UP_TO_LABEL = "et moins (et avant)"

    private val YEAR_REGEX = Regex("\\b(19\\d{2}|20\\d{2})\\b")

    /** Annees presentes dans un texte ("2018-2019" -> {2018, 2019}). */
    fun parse(value: String?): Set<Int> =
        YEAR_REGEX.findAll(value.orEmpty())
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it in 1900..2100 }
            .toSet()

    /** Vrai quand la categorie couvre une annee et toutes les precedentes. */
    fun isUpToAndBefore(value: String?): Boolean =
        value.orEmpty().contains("moins", ignoreCase = true)

    /** "2017", "2018-2019" (annees croissantes) ou "2012 et moins". */
    fun format(years: Collection<Int>, upToAndBefore: Boolean): String {
        val sorted = years.filter { it in 1900..2100 }.distinct().sorted()
        if (upToAndBefore) {
            return "${sorted.firstOrNull() ?: 2012}$UP_TO_SUFFIX"
        }
        return sorted.joinToString("-")
    }

    /** Valeurs par defaut d'un groupe (source : [JudoGroup.ageRange]). */
    fun defaultsFor(groupCode: String): Pair<Set<Int>, Boolean> {
        val range = JudoGroup.fromString(groupCode).ageRange
        return parse(range) to isUpToAndBefore(range)
    }

    /** Bornes proposees dans le selecteur (annee la plus ancienne -> la plus recente). */
    val selectableYears: List<Int> = (MIN_YEAR..MAX_YEAR).toList()
}
