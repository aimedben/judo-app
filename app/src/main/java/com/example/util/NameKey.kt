package com.example.util

import java.text.Normalizer

/**
 * Cle canonique des noms : insensible a la casse, aux accents et aux espaces multiples.
 *
 * Les rosters des seances passees sont des listes de noms ; un membre renomme, importe avec un
 * accent different ou double-espace doit rester le même athlete partout. Toute comparaison
 * d'appartenance aux listes presents/absents DOIT passer par [canonical].
 */
object NameKey {

    // Regex PRECOMPILEES : canonical() est appele des milliers de fois (rosters, recherche,
    // corrections d'emargement). Les compiler a chaque appel rendait l'ouverture des fenetres
    // de seance tres lente.
    private val combiningMarks = Regex("\\p{Mn}")
    private val whitespace = Regex("\\s+")

    fun canonical(name: String): String {
        val lowered = name.trim().lowercase().replace("’", "'")
        val noAccents = Normalizer.normalize(lowered, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
        return noAccents.replace(whitespace, " ").trim()
    }

    fun same(a: String, b: String): Boolean = canonical(a) == canonical(b)
}
