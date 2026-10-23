package com.example.util

import java.text.Normalizer
import java.util.Locale

/**
 * Cle de comparaison des noms de coachs.
 *
 * Les memes personnes apparaissent sous des formes legalement differentes selon la table :
 * "MELOUI IMANE" (coaches) vs "Coach MELOUI IMANE" (categories) vs
 * "BOURENANE LYES (Coach & DTS)". On ignore la casse, les accents, les parentheses et le
 * mot "coach" afin de reconnaitre la meme personne.
 */
object CoachNameKey {

    fun of(value: String?): String {
        val stripped = Normalizer.normalize(value?.trim().orEmpty(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase(Locale.ROOT)
        return stripped
            .replace(Regex("\\(.*?\\)"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .split(' ')
            .filter { it.isNotBlank() && it != "coach" }
            .joinToString(" ")
    }

    fun same(a: String?, b: String?): Boolean {
        val ka = of(a)
        return ka.isNotEmpty() && ka == of(b)
    }
}
