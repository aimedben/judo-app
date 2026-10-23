package com.example.util

import com.example.data.model.Member
import java.text.Normalizer
import java.util.Locale

/**
 * Detection des doublons d'athletes.
 *
 * Regle metier : un athlete ne doit JAMAIS etre ajoute deux fois. On considere
 * qu'un athlete existe deja si :
 *  - son code QR est identique (comparaison insensible a la casse), OU
 *  - ses nom + prenom sont identiques ET rien ne permet de le distinguer
 *    (meme date de naissance, meme telephone, ou aucune donnee discriminante).
 *
 * Deux athletes portant le meme nom dont la date de naissance OU le telephone
 * different sont consideres comme des personnes distinctes (le nom seul ne suffit
 * pas a conclure).
 *
 * Utilise a la fois par l'interface (blocage avant enregistrement) et par le
 * repository (garde-fou avant toute ecriture en base).
 */
object MemberDuplicates {

    /** Renvoie la premiere fiche existante en conflit avec [candidate], ou null. */
    fun find(existing: List<Member>, candidate: Member): Member? =
        existing.firstOrNull { conflicts(it, candidate) }

    /** Vrai si [existing] et [candidate] designent le meme athlete. */
    fun conflicts(existing: Member, candidate: Member): Boolean {
        // On ne se compare jamais a soi-meme lors d'une modification de fiche.
        if (candidate.id != 0L && existing.id == candidate.id) return false

        val candidateQr = candidate.qrCode.trim()
        val existingQr = existing.qrCode.trim()
        if (candidateQr.isNotBlank() && candidateQr.equals(existingQr, ignoreCase = true)) {
            return true
        }

        val candidateFirst = normalizeName(candidate.firstName)
        val candidateLast = normalizeName(candidate.lastName)
        if (candidateFirst.isBlank() || candidateLast.isBlank()) return false

        // Comparaison tolerante aux variantes d'ecriture : "BEN ALI" / "BEN-ALI" /
        // "BENALI" ou "ABD EL KADER" / "ABDELKADER" designent la meme personne.
        val sameFirst = normalizeName(existing.firstName) == candidateFirst ||
            compactName(existing.firstName) == compactName(candidate.firstName)
        if (!sameFirst) return false

        val sameLast = normalizeName(existing.lastName) == candidateLast ||
            compactName(existing.lastName) == compactName(candidate.lastName)
        if (!sameLast) return false

        // Meme identite : on cherche une preuve qu'il s'agit d'une autre personne.
        val candidateDob = candidate.birthDate.trim()
        val existingDob = existing.birthDate.trim()
        if (candidateDob.isNotBlank() && existingDob.isNotBlank() && candidateDob != existingDob) {
            return false
        }

        val candidatePhone = digitsOnly(candidate.phone)
        val existingPhone = digitsOnly(existing.phone)
        if (candidatePhone.isNotBlank() && existingPhone.isNotBlank() && candidatePhone != existingPhone) {
            return false
        }

        return true
    }

    /** Minuscules, sans accents, espaces multiples reduits. */
    fun normalizeName(value: String): String {
        val stripped = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
        return stripped.trim().lowercase(Locale.ROOT).replace(MULTI_SPACE, " ")
    }

    private fun digitsOnly(value: String): String = value.filter { it.isDigit() }

    /** Nom reduit a ses lettres/chiffres : "BEN-ALI" et "BEN ALI" deviennent "benali". */
    private fun compactName(value: String): String =
        normalizeName(value).replace(NON_WORD, "")

    private val NON_WORD = Regex("[^a-z0-9]")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val MULTI_SPACE = Regex("\\s+")
}
