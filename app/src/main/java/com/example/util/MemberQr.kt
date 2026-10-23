package com.example.util

import java.util.Locale

/**
 * Normalisation du code QR stocke sur la fiche d'un athlete.
 *
 * Regle metier : la colonne `qr_code` ne contient JAMAIS le contenu brut d'une carte
 * sportive (plusieurs lignes de texte). Elle contient :
 *  - un code de l'application : `JCS-355` (prefixe + numero de carte) ;
 *  - ou un code court imprime sur la carte : `JS-2026-0007`, `ABC123`.
 *
 * Le numero de carte est lu dans une charge utile structuree (« N: 355 »), quel que
 * soit le nombre de lignes / la presence de retours a la ligne.
 */
object MemberQr {

    const val PREFIX = "JCS-"
    private const val MAX_CODE_LENGTH = 24

    /** Un numero de carte est un entier de 1 a 8 chiffres. */
    private val DIGITS_ONLY = Regex("^\\d{1,8}$")

    /** Code deja produit par l'application : "JCS-355", "jcs 355", "JCS355". */
    private val APP_CODE = Regex("(?i)^\\s*jcs\\s*[-–—]?\\s*(\\d{1,8})\\s*$")

    /** Ligne « N: 355 », « N° 355 », « Numero de carte : 355 ». */
    private val LABELLED_LINE = Regex(
        "(?i)^\\s*(?:n\\s*[°ºo]?|num(?:[eé]ro)?(?:\\s*de)?(?:\\s*(?:carte|licence|cin))?)\\s*[:=.]?\\s*(\\d{1,8})\\s*$"
    )

    /** Ligne de numero enfouie dans une charge utile aplatie ("...SEDDOUKN: 355Adherent: ..."). */
    private val LABELLED_INLINE = Regex(
        "(?i)(?:^|[^\\p{L}])n\\s*[°ºo]?\\s*[:=.]\\s*(\\d{1,8})(?![\\d])"
    )

    /**
     * Numero colle directement a un champ de la carte ("...SEDDOUKN: 355Adherent: ...").
     * Exige qu'un libelle connu suive les chiffres : evite de confondre avec
     * "SAISON: 2026/2027" (qui ne doit jamais produire "JCS-2026").
     */
    private val CARD_NUMBER_BEFORE_FIELD = Regex(
        "(?i)n\\s*[°ºo]?\\s*[:=.]\\s*(\\d{1,8})(?=adherent|adh[eé]rent|nom|pr[eé]nom|n[eé]\\s*\\(?e\\)?\\s*le|naissance|saison|tel|categorie|cat[eé]gorie|ceinture)"
    )

    /**
     * Retourne le code QR a stocker, ou une chaine vide quand la valeur ne porte
     * aucun identifiant exploitable (texte de carte, paragraphe, etc.).
     *
     * @param raw valeur saisie / scannee / importee.
     * @param fallbackCardNumber numero de carte deja extrait (formulaire de fiche).
     */
    fun normalize(raw: String?, fallbackCardNumber: String? = null): String {
        val value = raw?.trim().orEmpty()

        // 0) La valeur est deja un code court : on ne touche qu'a la casse / au prefixe.
        if (value.isNotEmpty() && isShortCode(value)) {
            return toAppCode(value)
        }

        // 1) Numero de carte connnu par l'appelant ("N: 355" deja lu par le formulaire).
        fallbackCardNumber?.let { digitsOf(it) }?.let { return PREFIX + it }

        if (value.isEmpty()) return ""

        // 2) Numero porte par une ligne de la carte ("N: 355" seule sur sa ligne).
        value.lines()
            .map { it.trim() }
            .firstNotNullOfOrNull { LABELLED_LINE.find(it)?.groupValues?.get(1) }
            ?.let { return PREFIX + it }

        // 3) Numero colle a un champ connu dans une charge utile aplatie
        //    ("...SEDDOUKN: 355Adherent: ...").
        CARD_NUMBER_BEFORE_FIELD.find(value)?.groupValues?.getOrNull(1)
            ?.takeIf { it.isNotEmpty() && it.length <= 8 }
            ?.let { return PREFIX + it }

        // 3b) Numero enfoui dans une charge utile aplatie (retours a la ligne perdus).
        LABELLED_INLINE.find(value)?.groupValues?.getOrNull(1)
            ?.takeIf { it.isNotEmpty() && it.length <= 8 }
            ?.let { return PREFIX + it }

        // 4) Dernier recours : le parseur de cartes du club.
        val parsed = CardDataParser.parseCardData(value)
        digitsOf(parsed.cardNumber)?.let { return PREFIX + it }
        if (parsed.qrCode.isNotBlank() && isShortCode(parsed.qrCode)) return toAppCode(parsed.qrCode)

        // 5) Charge utile de carte sans numero : jamais stockee comme code QR.
        return ""
    }

    /** Vrai si [value] est deja un code QR propre (courte chaine, sans espaces). */
    fun isClean(raw: String?): Boolean {
        val value = raw?.trim().orEmpty()
        return value.isNotEmpty() && isShortCode(value) && toAppCode(value) == value
    }

    private fun isShortCode(value: String): Boolean =
        value.length <= MAX_CODE_LENGTH && !value.contains(' ') && !value.contains('\n') &&
            value.any { it.isLetterOrDigit() }

    /** "jcs 355" / "JCS355" / "355" -> "JCS-355" ; autre code court -> majuscules. */
    private fun toAppCode(value: String): String {
        APP_CODE.find(value)?.groupValues?.get(1)?.let { return PREFIX + it }
        if (DIGITS_ONLY.matches(value)) return PREFIX + value
        return value.uppercase(Locale.ROOT)
    }

    private fun digitsOf(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        APP_CODE.find(trimmed)?.groupValues?.get(1)?.let { return it }
        if (DIGITS_ONLY.matches(trimmed)) return trimmed
        LABELLED_LINE.find(trimmed)?.groupValues?.get(1)?.let { return it }
        return null
    }
}
