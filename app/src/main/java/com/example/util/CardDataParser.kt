package com.example.util

import com.example.data.model.Belt
import com.example.data.model.JudoGroup
import org.json.JSONObject

data class ExtractedMemberInfo(
    val firstName: String = "",
    val lastName: String = "",
    val birthDate: String = "",
    val phone: String = "",
    val notes: String = "",
    val qrCode: String = "",
    val cardNumber: String = "",
    val season: String = "2024-2025",
    val photoUrl: String = "",
    val group: JudoGroup = JudoGroup.POUSSINS,
    val belt: Belt = Belt.BLANCHE,
    val rawScannedData: String = "",
    val clubName: String = ""
)

/**
 * Extracts member information from :
 *  - a JSON payload,
 *  - a delimited key/value payload (KEY:VALUE;KEY:VALUE),
 *  - the printed labels of a French sport licence / club card (the OCR text of a card photo),
 *  - a plain card number / QR payload (e.g. "JCS-0012").
 *
 * The parser is defensive: a value that does not look like a phone number, a date or a card number
 * is never accepted (this avoids storing a whole OCR blob as the card number).
 */
object CardDataParser {

    // ---------------------------------------------------------------------------------------------
    // Date helpers
    // ---------------------------------------------------------------------------------------------

    private val ISO_DATE = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})")
    private val ISO_DATE_SLASH = Regex("(\\d{4})/(\\d{1,2})/(\\d{1,2})")
    private val FR_DATE = Regex("(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4})")
    private val SEASON_RANGE = Regex("(\\d{4})\\s*[/\\-]\\s*(\\d{4})")

    /** Normalizes "2026/2027", "2026 - 2027" or "Saison: 2026/2027" to "2026-2027". */
    fun normalizeSeason(value: String): String {
        SEASON_RANGE.find(value)?.let { m ->
            return "${m.groupValues[1]}-${m.groupValues[2]}"
        }
        return value.trim()
    }

    /** Returns a normalized "yyyy-MM-dd" date found inside [value], or "" when there is none. */
    fun extractDate(value: String): String {
        ISO_DATE.find(value)?.let { m ->
            return "%04d-%02d-%02d".format(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()
            )
        }
        ISO_DATE_SLASH.find(value)?.let { m ->
            return "%04d-%02d-%02d".format(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()
            )
        }
        FR_DATE.find(value)?.let { m ->
            var day = m.groupValues[1].toInt()
            var month = m.groupValues[2].toInt()
            val year = m.groupValues[3].toInt()
            // French cards use dd/MM/yyyy. Swap when the first number cannot be a day.
            if (day > 31 || (day > 12 && month <= 12)) {
                val tmp = day; day = month; month = tmp
            } else if (month > 12 && day <= 12) {
                val tmp = day; day = month; month = tmp
            }
            if (day in 1..31 && month in 1..12 && year in 1900..2100) {
                return "%04d-%02d-%02d".format(year, month, day)
            }
        }
        return ""
    }

    // ---------------------------------------------------------------------------------------------
    // Phone / card number helpers
    // ---------------------------------------------------------------------------------------------

    private val PHONE_IN_TEXT = Regex("(?:\\+?\\d[\\d\\s.\\-]{7,})")

    /** Returns a digit-only phone number (keeping a leading +) or "". */
    fun extractPhone(value: String): String {
        val candidates = PHONE_IN_TEXT.findAll(value).map { it.value }.toList()
        val best = candidates.maxByOrNull { it.count { c -> c.isDigit() } } ?: return ""
        val digits = best.filter { it.isDigit() }
        if (digits.length < 8 || digits.length > 15) return ""
        return if (best.trim().startsWith("+")) "+$digits" else digits
    }

    /** A card / licence number is a short alphanumeric token, never a sentence. */
    private val CARD_NUMBER = Regex("^[A-Za-z0-9][A-Za-z0-9\\-/_]{0,14}$")

    fun extractCardNumber(value: String): String {
        val cleaned = value.replace('\n', ' ').trim()
        if (cleaned.isEmpty()) return ""
        if (CARD_NUMBER.matches(cleaned)) return cleaned
        // Take the first token that looks like a card number (e.g. "N° 61 - Saison 2026" -> "61").
        return cleaned.split(Regex("[\\s,]+"))
            .firstOrNull { CARD_NUMBER.matches(it) }
            ?: ""
    }

    // ---------------------------------------------------------------------------------------------
    // Name helpers
    // ---------------------------------------------------------------------------------------------

    private val CLUB_WORDS = listOf(
        "judo", "club", "seddouk", "ligue", "federation", "fédération", "dojo", "tatami",
        "licence", "saison", "ceinture", "carte", "adherent", "adhérent", "sexe", "categorie",
        "catégorie", "groupe", "masculin", "feminin", "féminin", "willaya", "wilaya", "commune"
    )

    /** "ANIS BOURENANE" -> "Anis Bourenane" ; "ait ouali" -> "Ait Ouali". */
    fun smartCase(value: String): String {
        val cleaned = value.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return ""
        // Keep names that already mix upper and lower case untouched (assume they are correct).
        val letters = cleaned.filter { it.isLetter() }
        if (letters.isNotEmpty() && letters.any { it.isLowerCase() } && letters.any { it.isUpperCase() }) {
            return cleaned
        }
        return cleaned.split(' ').joinToString(" ") { part ->
            if (part.isEmpty()) part
            else part.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    private fun looksLikeName(value: String): Boolean {
        val cleaned = value.replace(Regex("[^\\p{L}\\s'\\-]"), " ").replace(Regex("\\s+"), " ").trim()
        if (cleaned.length < 3 || cleaned.length > 40) return false
        val tokens = cleaned.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty() || tokens.size > 4) return false
        val normalized = cleaned.lowercase()
        return CLUB_WORDS.none { normalized == it || normalized.startsWith("$it ") }
    }

    /** Splits a "PRENOM NOM" (or "NOM PRENOM" when [lastNameFirst]) payload. */
    private fun splitFullName(value: String, lastNameFirst: Boolean = false): Pair<String, String> {
        val cleaned = value.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return "" to ""
        if (cleaned.contains(',')) {
            val parts = cleaned.split(',', limit = 2)
            val last = smartCase(parts[0])
            val first = smartCase(parts.getOrElse(1) { "" })
            if (last.isNotBlank() && first.isNotBlank()) return first to last
        }
        val tokens = cleaned.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return "" to ""
        if (tokens.size == 1) return smartCase(tokens[0]) to ""
        return if (lastNameFirst) {
            smartCase(tokens.last()) to smartCase(tokens.dropLast(1).joinToString(" "))
        } else {
            smartCase(tokens.first()) to smartCase(tokens.drop(1).joinToString(" "))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Public entry point
    // ---------------------------------------------------------------------------------------------

    fun parseCardData(raw: String): ExtractedMemberInfo {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ExtractedMemberInfo()

        parseJson(trimmed)?.let { return it }
        parseDelimited(trimmed)?.let { return it }
        parseFrenchCard(trimmed)?.let { return it }
        parseHeuristic(trimmed)?.let { return it }

        // Simple code / card number payload (e.g. "JCS-0012", "123456").
        // Only short single-line payloads can be a bare identifier: anything longer is a card body
        // and must never be stored as a card number / QR code.
        val shortCode = extractCardNumber(trimmed)
        val isShortPayload = trimmed.length <= 24 &&
            !trimmed.contains('\n') &&
            trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }.size <= 2
        val usableCode = shortCode.takeIf {
            isShortPayload &&
                it.any { c -> c.isDigit() } &&
                CLUB_WORDS.none { word -> it.lowercase() == word }
        }
        return ExtractedMemberInfo(
            qrCode = when {
                !isShortPayload -> ""
                trimmed.startsWith("JCS-", ignoreCase = true) -> trimmed
                usableCode != null -> "JCS-$usableCode"
                else -> ""
            },
            cardNumber = usableCode ?: "",
            rawScannedData = trimmed
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 1. JSON
    // ---------------------------------------------------------------------------------------------

    private fun parseJson(trimmed: String): ExtractedMemberInfo? {
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        return try {
            val json = JSONObject(trimmed)
            val firstName = json.optString("firstName", json.optString("prenom", ""))
            val lastName = json.optString("lastName", json.optString("nom", ""))
            val birthDate = extractDate(json.optString("birthDate", json.optString("dateNaissance", "")))
            val phone = extractPhone(json.optString("phone", json.optString("telephone", "")))
            val cardNumber = extractCardNumber(
                json.optString("cardNumber", json.optString("licence", json.optString("numero", "")))
            )
            val qrCode = json.optString(
                "qrCode",
                if (cardNumber.isNotBlank()) "JCS-$cardNumber" else ""
            )
            val beltStr = json.optString("belt", json.optString("ceinture", "BLANCHE"))
            val groupStr = json.optString("group", json.optString("categorie", ""))
            val season = json.optString("season", json.optString("saison", ""))
            val photoUrl = json.optString("photoUrl", json.optString("photo", ""))
            val notes = json.optString("notes", "")

            val group = if (groupStr.isNotBlank()) JudoGroup.fromString(groupStr)
            else if (birthDate.isNotBlank()) JudoGroup.fromBirthDate(birthDate)
            else JudoGroup.POUSSINS

            ExtractedMemberInfo(
                firstName = smartCase(firstName),
                lastName = smartCase(lastName),
                birthDate = birthDate,
                phone = phone,
                notes = notes,
                qrCode = qrCode.ifBlank { if (cardNumber.isNotBlank()) "JCS-$cardNumber" else "" },
                cardNumber = cardNumber,
                season = season.ifBlank { DEFAULT_SEASON },
                photoUrl = photoUrl,
                group = group,
                belt = Belt.fromString(beltStr),
                rawScannedData = trimmed
            )
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 2. Delimited KEY:VALUE;KEY:VALUE
    // ---------------------------------------------------------------------------------------------

    private fun parseDelimited(trimmed: String): ExtractedMemberInfo? {
        if (!trimmed.contains(";") && !trimmed.contains("|")) return null
        val delimiter = if (trimmed.contains(";")) ";" else "|"
        val map = mutableMapOf<String, String>()
        for (part in trimmed.split(delimiter)) {
            val kv = part.split(":", "=")
            if (kv.size == 2) map[kv[0].trim().uppercase()] = kv[1].trim()
        }
        if (map.isEmpty()) return null

        val fullName = map["NOM_PRENOM"] ?: map["ADHERENT"] ?: map["NOMETPRENOM"] ?: ""
        val (splitFirst, splitLast) = if (fullName.isNotBlank()) splitFullName(fullName) else "" to ""
        val birthDate = extractDate(map["BIRTHDATE"] ?: map["DOB"] ?: map["NAISSANCE"] ?: "")
        val cardNumber = extractCardNumber(map["CARD"] ?: map["LICENCE"] ?: map["ID"] ?: "")
        val firstName = smartCase(map["FIRSTNAME"] ?: map["PRENOM"] ?: splitFirst)
        val lastName = smartCase(map["LASTNAME"] ?: map["NOM"] ?: splitLast)
        if (firstName.isBlank() && lastName.isBlank() && cardNumber.isBlank() &&
            birthDate.isBlank() && extractPhone(map["PHONE"] ?: map["TEL"] ?: "").isBlank()
        ) {
            return null
        }
        return ExtractedMemberInfo(
            firstName = firstName,
            lastName = lastName,
            birthDate = birthDate,
            phone = extractPhone(map["PHONE"] ?: map["TEL"] ?: ""),
            cardNumber = cardNumber,
            qrCode = map["QR"]?.ifBlank { null } ?: if (cardNumber.isNotBlank()) "JCS-$cardNumber" else "",
            season = (map["SEASON"] ?: map["SAISON"] ?: "").ifBlank { DEFAULT_SEASON },
            group = if (birthDate.isNotBlank()) JudoGroup.fromBirthDate(birthDate) else JudoGroup.POUSSINS,
            belt = Belt.fromString(map["BELT"] ?: map["CEINTURE"] ?: "BLANCHE"),
            rawScannedData = trimmed
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 3. French printed card labels
    // ---------------------------------------------------------------------------------------------

    /**
     * Known labels, matched case-insensitively and accent-insensitively.
     * A value runs from the label up to the next known label, which works for:
     * newline separated cards, "Label: value" on one line, values on the following line and
     * fully concatenated payloads.
     */
    private val CARD_LABEL_REGEX = Regex(
        "(?i:(n°\\s*(?:de\\s*)?(?:licence|carte|cin)?|no\\s*(?:de\\s*)?(?:licence|carte)?|" +
            "num(?:ero|éro)?\\s*(?:de\\s*)?(?:licence|carte)?|licence|lic|" +
            "adherents?|adhérents?|judoka|membre|" +
            "pr[ée]noms?|noms?|nom\\s*(?:et|&)\\s*pr[ée]noms?|" +
            "n[ée]e?\\s*\\(?\\s*e\\s*\\)?\\s*le|n[ée]e?\\s*le|n[ée]e?\\s*:\\s*|date\\s*(?:de\\s*)?naissance|naissance|" +
            "t[ée]l[ée]?\\s*\\.?\\s*(?:du\\s*)?(?:tuteur|p[èe]re|m[èe]re)?|portable|mobile|gsm|tuteur|" +
            "saison|season|ceinture|grade|belt|" +
            "sexe|cat[ée]gorie|groupe|club|ligue|dojo|" +
            "autorisation|signature|validit[ée]|photo|date\\s*d[’']?inscription))\\s*[:=]\\s*"
    )

    /** Labels whose content must be ignored (administrative / non-member fields). */
    private val IGNORED_LABELS = setOf(
        "sexe", "categorie", "groupe", "club", "ligue", "dojo", "autorisation",
        "signature", "validite", "photo", "dateinscription"
    )

    private fun parseFrenchCard(raw: String): ExtractedMemberInfo? {
        val matches = CARD_LABEL_REGEX.findAll(raw).toList()
        if (matches.isEmpty()) return null

        var firstName = ""
        var lastName = ""
        var fullName = ""
        var birthDate = ""
        var phone = ""
        var cardNumber = ""
        var season = ""
        var beltStr = ""

        matches.forEachIndexed { index, match ->
            val label = normalizeCardLabel(match.groupValues[1])
            if (label.isEmpty() || IGNORED_LABELS.any { label.startsWith(it) }) return@forEachIndexed

            val valueStart = match.range.last + 1
            val valueEnd = if (index + 1 < matches.size) matches[index + 1].range.first else raw.length
            if (valueEnd <= valueStart) return@forEachIndexed

            val value = raw.substring(valueStart, valueEnd)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim()
                .trim(';', '|', ',', ' ', '\u2022', '-', ':')
            if (value.isBlank()) return@forEachIndexed

            when {
                label.startsWith("prenom") -> if (firstName.isBlank()) firstName = smartCase(value)
                label.startsWith("nom") && !label.startsWith("num") ->
                    if (lastName.isBlank()) lastName = smartCase(value)

                label.startsWith("adherent") || label == "judoka" || label == "membre" ->
                    if (fullName.isBlank()) fullName = value

                label.startsWith("neele") || label.startsWith("nele") || label.startsWith("ne") ||
                    label.startsWith("date") || label == "naissance" -> {
                    if (birthDate.isBlank()) birthDate = extractDate(value)
                }

                label.startsWith("tel") || label.startsWith("portable") || label == "mobile" ||
                    label == "gsm" || label == "tuteur" -> {
                    if (phone.isBlank()) phone = extractPhone(value)
                }

                label == "n" || label == "no" || label == "n0" || label.startsWith("num") ||
                    label.startsWith("licence") || label.startsWith("lic") -> {
                    if (cardNumber.isBlank()) cardNumber = extractCardNumber(value)
                }

                label.startsWith("saison") || label == "season" -> if (season.isBlank()) season = normalizeSeason(value)

                label.startsWith("ceinture") || label == "grade" || label == "belt" ->
                    if (beltStr.isBlank()) beltStr = value
            }
        }

        // "Adherent: ANIS BOURENANE" carries both the first and the last name.
        if (fullName.isNotBlank()) {
            val (first, last) = splitFullName(fullName)
            if (firstName.isBlank()) firstName = first
            if (lastName.isBlank()) lastName = last
        }

        val hasName = firstName.isNotBlank() || lastName.isNotBlank()
        if (!hasName && birthDate.isBlank() && phone.isBlank() && cardNumber.isBlank()) return null

        return ExtractedMemberInfo(
            firstName = firstName,
            lastName = lastName,
            birthDate = birthDate,
            phone = phone,
            cardNumber = cardNumber,
            qrCode = if (cardNumber.isNotBlank()) "JCS-$cardNumber" else "",
            season = normalizeSeason(season.ifBlank { DEFAULT_SEASON }),
            group = if (birthDate.isNotBlank()) JudoGroup.fromBirthDate(birthDate) else JudoGroup.POUSSINS,
            belt = Belt.BLANCHE,
            rawScannedData = raw
        )
    }

    private fun normalizeCardLabel(label: String): String {
        val sb = StringBuilder()
        for (ch in label.trim()) {
            val c = when (ch) {
                'é', 'è', 'ê', 'ë', 'É', 'È', 'Ê', 'Ë' -> 'e'
                'à', 'â', 'ä', 'À', 'Â', 'Ä' -> 'a'
                'î', 'ï', 'Î', 'Ï' -> 'i'
                'ô', 'ö', 'Ô', 'Ö' -> 'o'
                'ù', 'û', 'ü', 'Ù', 'Û', 'Ü' -> 'u'
                'ç', 'Ç' -> 'c'
                else -> ch.lowercaseChar()
            }
            if (c.isLetterOrDigit()) sb.append(c)
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------------------------------------
    // 4. Last resort: line based heuristics (unlabelled card)
    // ---------------------------------------------------------------------------------------------

    private fun parseHeuristic(raw: String): ExtractedMemberInfo? {
        val lines = raw.split('\n', '\r').map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        var firstName = ""
        var lastName = ""
        var birthDate = ""
        var phone = ""
        var cardNumber = ""
        var season = ""

        for (line in lines) {
            val withoutLabel = line.substringAfter(':', line).trim().ifBlank { line }
            val lineLower = line.lowercase()

            if (birthDate.isBlank()) {
                val date = extractDate(line)
                if (date.isNotBlank()) {
                    birthDate = date
                    continue
                }
            }
            if (phone.isBlank() && (lineLower.contains("tel") || line.filter { it.isDigit() }.length >= 8)) {
                val found = extractPhone(line)
                if (found.isNotBlank()) {
                    phone = found
                    continue
                }
            }
            if (season.isBlank() && (lineLower.contains("saison") || SEASON_RANGE.containsMatchIn(line))) {
                season = normalizeSeason(line)
                continue
            }
            if (firstName.isBlank() && looksLikeName(withoutLabel)) {
                val (first, last) = splitFullName(withoutLabel)
                firstName = first
                lastName = last
                continue
            }
            if (cardNumber.isBlank() && line.filter { it.isDigit() }.length in 1..12 &&
                line.none { it.isLetter() }
            ) {
                cardNumber = extractCardNumber(line)
            }
        }

        if (firstName.isBlank() && lastName.isBlank() && birthDate.isBlank() &&
            phone.isBlank() && cardNumber.isBlank()
        ) {
            return null
        }

        return ExtractedMemberInfo(
            firstName = firstName,
            lastName = lastName,
            birthDate = birthDate,
            phone = phone,
            cardNumber = cardNumber,
            qrCode = if (cardNumber.isNotBlank()) "JCS-$cardNumber" else "",
            season = season.ifBlank { DEFAULT_SEASON },
            group = if (birthDate.isNotBlank()) JudoGroup.fromBirthDate(birthDate) else JudoGroup.POUSSINS,
            rawScannedData = raw
        )
    }

    private const val DEFAULT_SEASON = "2024-2025"
}
