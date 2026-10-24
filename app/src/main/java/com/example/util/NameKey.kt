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

    fun canonical(name: String): String {
        val lowered = name.trim().lowercase().replace("’", "'")
        val noAccents = Normalizer.normalize(lowered, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}"), "")
        return noAccents.replace(Regex("\\s+"), " ").trim()
    }

    fun same(a: String, b: String): Boolean = canonical(a) == canonical(b)
}
