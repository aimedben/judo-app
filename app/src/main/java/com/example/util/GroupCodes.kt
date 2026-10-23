package com.example.util

import com.example.data.model.JudoGroup
import java.util.Locale

/**
 * Correspondance athlete <-> categorie.
 *
 * Source de verite UNIQUE : la colonne `group_code` de la table Supabase
 * `judo_categories` (et de `scheduled_sessions`, qui la recopie). Ces valeurs sont
 * exactement les codes portes par les athletes : `EVEIL`, `U9`, `U11`, `U13`,
 * `U15`, `U18`, `SENIOR`.
 *
 * Une categorie peut couvrir plusieurs groupes : "Cadets/Juniors/Seniors" porte
 * `U18,SENIOR`. La comparaison est donc faite sur des jetons separes par
 * virgule / point-virgule / slash, sans aucune liste de libelles codee en dur.
 */
object GroupCodes {

    private val SEPARATORS = Regex("[,;/|]+")

    /** "U18,SENIOR" -> ["U18", "SENIOR"]. */
    fun split(categoryGroupCode: String?): List<String> =
        categoryGroupCode.orEmpty()
            .split(SEPARATORS)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** Jetons normalises (majuscules) : "u18, senior" -> ["U18", "SENIOR"]. */
    fun canonical(raw: String?): List<String> =
        split(raw).map { it.uppercase(Locale.ROOT) }

    /** Vrai si un athlete du groupe [memberGroupCode] appartient a la categorie. */
    fun matches(categoryGroupCode: String?, memberGroupCode: String?): Boolean {
        val category = canonical(categoryGroupCode)
        val member = canonical(memberGroupCode)
        if (category.isEmpty() || member.isEmpty()) return false
        return category.any { it in member } || member.any { it in category }
    }

    /** Libelle lisible d'un code : "U18" -> "Cadets (U18)". */
    fun label(categoryGroupCode: String?): String =
        canonical(categoryGroupCode)
            .map { code -> JudoGroup.entries.firstOrNull { it.code == code }?.label ?: code }
            .distinct()
            .joinToString(", ")
}
