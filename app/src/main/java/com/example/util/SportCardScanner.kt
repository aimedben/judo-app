package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.example.data.model.Belt
import com.example.data.model.JudoGroup
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.util.Locale

object SportCardScanner {

    /** Club administration phone number printed on cards (must NOT be assigned to athlete/tutor) */
    private const val CLUB_PHONE_CLEAN = "0794335010"

    /**
     * Recognises a barcode payload that carries named card fields ("N: 121", "Adherent: ...")
     * as opposed to a bare serial number, so the structured club QR outranks a serial barcode.
     */
    private val STRUCTURED_CARD_PAYLOAD_REGEX = Regex(
        """(?i)(?:^|\n|\s)(?:n\s*[:=]|adherent|adhérent|nom\s*[:=]|prénom|prenom|naissance|saison)"""
    )

    /** Parse a raw (already extracted) card text or QR payload. */
    fun parseCardText(text: String): ExtractedMemberInfo = CardDataParser.parseCardData(text)

    /**
     * Picks which detected barcode payload to trust.
     *
     * A card usually carries the structured club QR (club, N°, name, DOB, tutor phone, season) and
     * a bare serial barcode next to it. The structured payload must win regardless of detection
     * order, because it is the identifier the club uses everywhere else — and the very payload a
     * douchette reads when the same card is scanned at the dojo. Falling back to the first
     * barcode keeps working for plain codes.
     */
    internal fun chooseCardBarcodePayload(values: List<String>): String? =
        values.firstOrNull { STRUCTURED_CARD_PAYLOAD_REGEX.containsMatchIn(it) } ?: values.firstOrNull()

    /**
     * Complete on-device scanning & extraction pipeline for sports cards:
     * 1. Detects text via ML Kit OCR (Nom, Prénom, N°, Date de naissance, Tél tuteur, Saison...)
     * 2. Detects any QR Code / Barcode on the card via ML Kit Barcode
     *    - If QR contains full sports card data, decodes it for 100% precision
     * 3. Detects and crops the athlete's portrait photo via ML Kit Face Detection
     * 4. Merges QR data and OCR data without inserting fake/fixed dummy values
     */
    suspend fun processCardBitmap(
        context: Context,
        bitmap: Bitmap
    ): Result<ExtractedMemberInfo> = withContext(Dispatchers.Default) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            // 1. Text Recognition (OCR)
            val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val visionText = try {
                textRecognizer.process(inputImage).await()
            } catch (e: Exception) {
                null
            }
            val rawText = visionText?.text ?: ""

            // 2. Barcode / QR Code Scanning
            val barcodeScanner = BarcodeScanning.getClient()
            val barcodes = try {
                barcodeScanner.process(inputImage).await()
            } catch (e: Exception) {
                emptyList()
            }
            // A club card carries the structured club QR (club, N°, name, DOB, tutor phone,
            // season) and often a bare serial barcode as well. Prefer the structured payload
            // whatever the detection order: it is the identifier the club uses everywhere else,
            // and it is exactly what a douchette reads from the same card's QR later on. Taking
            // the first barcode blindly used to store a serial (e.g. 262029) as the athlete's
            // card number, which no scan of that card could ever match again.
            val barcodeValues = barcodes.mapNotNull { it.rawValue?.trim()?.takeIf(String::isNotBlank) }
            val qrCodeValue = chooseCardBarcodePayload(barcodeValues)

            // 3. Face Detection & Portrait Photo Cropping
            var photoUriString = ""
            try {
                val faceDetector = FaceDetection.getClient(
                    FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .build()
                    )
                val faces = faceDetector.process(inputImage).await()

                val targetRect: Rect = if (faces.isNotEmpty()) {
                    // Pick the most prominent face on the card
                    val bestFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
                    val box = bestFace.boundingBox

                    // Add passport-style margins
                    val marginX = (box.width() * 0.40f).toInt()
                    val marginTop = (box.height() * 0.50f).toInt()
                    val marginBottom = (box.height() * 0.70f).toInt()

                    val left = (box.left - marginX).coerceAtLeast(0)
                    val top = (box.top - marginTop).coerceAtLeast(0)
                    val right = (box.right + marginX).coerceAtMost(bitmap.width)
                    val bottom = (box.bottom + marginBottom).coerceAtMost(bitmap.height)

                    Rect(left, top, right, bottom)
                } else {
                    // Fallback to standard Judo Seddouk ID-1 card photo position (left ~35% of card)
                    val left = (bitmap.width * 0.04f).toInt()
                    val top = (bitmap.height * 0.18f).toInt()
                    val right = (bitmap.width * 0.38f).toInt()
                    val bottom = (bitmap.height * 0.85f).toInt()
                    Rect(left, top, right, bottom)
                }

                if (targetRect.width() > 30 && targetRect.height() > 30) {
                    val croppedBitmap = Bitmap.createBitmap(
                        bitmap,
                        targetRect.left,
                        targetRect.top,
                        targetRect.width(),
                        targetRect.height()
                    )

                    // Save to local private files
                    val photosDir = File(context.filesDir, "athletes_photos").apply { mkdirs() }
                    val photoFile = File(photosDir, "judoka_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(photoFile).use { out ->
                        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    photoUriString = Uri.fromFile(photoFile).toString()
                }
            } catch (_: Exception) {
                // Photo extraction failure should not block text extraction
            }

            // 4. Try parsing structured data from QR Code first if present
            val qrParsed: ExtractedMemberInfo? = if (!qrCodeValue.isNullOrBlank()) {
                try {
                    val parsed = CardDataParser.parseCardData(qrCodeValue)
                    if (parsed.lastName.isNotBlank() || parsed.firstName.isNotBlank() || parsed.cardNumber.isNotBlank()) {
                        parsed
                    } else null
                } catch (_: Exception) {
                    null
                }
            } else null

            // 5. Parse OCR text using enhanced Card Parser
            val ocrParsed = parseCardOcrText(
                rawText = rawText,
                detectedQr = qrCodeValue,
                extractedPhotoUrl = photoUriString
            )

            // 6. Merge results: QR code is authoritative when available, supplemented by OCR and photo
            val mergedResult = mergeQrAndOcr(qrParsed, ocrParsed, qrCodeValue, photoUriString, rawText)

            Result.success(mergedResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Merges the QR payload with the OCR reading: the QR embedded in the club card is authoritative,
     * the OCR fills what the QR does not carry, and the extracted photo is kept either way.
     */
    internal fun mergeQrAndOcr(
        qrParsed: ExtractedMemberInfo?,
        ocrParsed: ExtractedMemberInfo,
        qrCodeValue: String?,
        photoUriString: String,
        rawText: String = ""
    ): ExtractedMemberInfo {
        if (qrParsed == null) return ocrParsed

        val finalBirth = qrParsed.birthDate.ifBlank { ocrParsed.birthDate }
        val finalGroup = if (qrParsed.birthDate.isNotBlank()) {
            JudoGroup.fromBirthDate(qrParsed.birthDate)
        } else {
            ocrParsed.group
        }
        return ExtractedMemberInfo(
            cardNumber = qrParsed.cardNumber.ifBlank { ocrParsed.cardNumber },
            firstName = qrParsed.firstName.ifBlank { ocrParsed.firstName },
            lastName = qrParsed.lastName.ifBlank { ocrParsed.lastName },
            birthDate = finalBirth,
            phone = qrParsed.phone.ifBlank { ocrParsed.phone },
            season = qrParsed.season.ifBlank { ocrParsed.season },
            group = finalGroup,
            belt = if (qrParsed.belt != Belt.BLANCHE) qrParsed.belt else ocrParsed.belt,
            // The printed QR carries the club payload, so the stored identifier must be the same one
            // the douchette derives from that payload ("JCS-<numéro>"), otherwise a member registered
            // by scanning the card could never be found by scanning that card.
            qrCode = when {
                qrParsed.cardNumber.isNotBlank() ->
                    qrParsed.qrCode.ifBlank { "JCS-${qrParsed.cardNumber}" }
                !qrCodeValue.isNullOrBlank() -> qrCodeValue
                else -> ocrParsed.qrCode
            },
            photoUrl = photoUriString.ifBlank { qrParsed.photoUrl },
            clubName = qrParsed.clubName.ifBlank { ocrParsed.clubName },
            notes = qrParsed.notes.ifBlank { ocrParsed.notes },
            rawScannedData = if (rawText.isNotBlank()) rawText else qrCodeValue ?: ""
        )
    }

    /**
     * Enhanced text parser tailored for Judo Club Seddouk and standard sports cards.
     * Accurately extracts without injecting fake or hardcoded values.
     */
    fun parseCardOcrText(
        rawText: String,
        detectedQr: String? = null,
        extractedPhotoUrl: String = ""
    ): ExtractedMemberInfo {
        var cardNumber = ""
        var lastName = ""
        var firstName = ""
        var birthDate = ""
        var phone = ""
        var season = ""
        var clubName = "JUDO CLUB SEDDOUK"
        var detectedBelt: Belt? = null
        var detectedCategory: JudoGroup? = null

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }

        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase(Locale.ROOT)

            // Club Name
            if (lower.contains("judo club seddouk") || lower.contains("j.c.s") || lower.contains("seddouk")) {
                clubName = "JUDO CLUB SEDDOUK"
            }

            // Saison (e.g. "SAISON 2026/2027", "Saison: 2026/2027")
            if (season.isBlank()) {
                val seasonRegex = Regex("""(?:saison|season)\s*[:=.]?\s*([0-9]{4}\s*[/_-]\s*[0-9]{4})""", RegexOption.IGNORE_CASE)
                seasonRegex.find(line)?.let {
                    season = it.groupValues[1].replace(" ", "").replace("_", "/").replace("-", "/")
                }
            }

            // Card Number / N° (e.g. "N: 1", "N° 1", "N° de carte: 1", "Carte N° 1")
            // The label MUST carry an explicit marker (° or a : / = separator) so that a
            // plain word such as "Nom" or a name starting with "N" (e.g. "NASSIM") is never
            // mistaken for the card number (which used to leak the athlete's name into the
            // QR / card-number field).
            if (cardNumber.isBlank()) {
                val nRegex = Regex(
                    """(?:^|\s)(?:n\s*[°º]\s*(?:de\s+)?(?:carte|licence|cin)?|n\s*[:=]|num[eé]ro\s*(?:de\s+)?(?:carte|licence)?|carte\s*n\s*[°º]?|licence)\s*[:=.]?\s*([0-9A-Za-z_-]+)""",
                    RegexOption.IGNORE_CASE
                )
                val match = nRegex.find(line)
                if (match != null) {
                    val candidate = match.groupValues[1].trim()
                    if (candidate.isNotEmpty() && candidate.any { it.isDigit() } &&
                        !candidate.equals("seddouk", ignoreCase = true)
                    ) {
                        cardNumber = candidate
                    }
                } else {
                    // Header on its own line ("N°", "N:", "N° de carte") with the number below
                    val isNHeader = lower.matches(
                        Regex("""^(?:n\s*[°º]|n\s*[:=]|num[eé]ro|carte\s*n\s*[°º]?|licence)\s*(?:de\s*)?(?:carte|licence|cin)?\s*[:=.]?$""")
                    )
                    if (isNHeader && i + 1 < lines.size) {
                        val next = lines[i + 1].trim()
                        if (next.matches(Regex("""^[0-9A-Za-z_-]{1,15}$""")) && next.any { it.isDigit() }) {
                            cardNumber = next
                        }
                    }
                }
            }

            // Combined Nom & Prénom on one line:
            // e.g. "NOM BOURENANE PRENOM ANIS" or "NOM : BOURENANE PRÉNOM : ANIS"
            if (lastName.isBlank() || firstName.isBlank()) {
                val combinedRegex = Regex("""(?i)nom\s*[:=.]?\s*([A-Za-zÀ-ÖØ-öø-ÿ'-]+)\s+(?:prénom|prenom)\s*[:=.]?\s*([A-Za-zÀ-ÖØ-öø-ÿ'-]+)""")
                val combMatch = combinedRegex.find(line)
                if (combMatch != null) {
                    if (lastName.isBlank()) lastName = combMatch.groupValues[1].trim().uppercase(Locale.ROOT)
                    if (firstName.isBlank()) firstName = formatName(combMatch.groupValues[2].trim())
                }
            }

            // Nom (Last Name)
            if (lastName.isBlank()) {
                val nomRegex = Regex("""(?:^|\s)nom\s*[:=.]?\s*([A-Za-zÀ-ÖØ-öø-ÿ\s'-]+)""", RegexOption.IGNORE_CASE)
                val match = nomRegex.find(line)
                if (match != null) {
                    val candidate = match.groupValues[1].trim()
                    if (candidate.isNotBlank() && !candidate.equals("de", ignoreCase = true) &&
                        !candidate.contains("naissance", ignoreCase = true) && !candidate.contains("prenom", ignoreCase = true)
                    ) {
                        lastName = candidate.uppercase(Locale.ROOT)
                    }
                } else {
                    // Multi-line: line is just "NOM" or "NOM :"
                    val isNomHeader = lower.matches(Regex("""^nom\s*[:=.]?$"""))
                    if (isNomHeader && i + 1 < lines.size) {
                        val next = lines[i + 1].trim()
                        if (!isLabelLine(next) && next.matches(NAME_LINE_REGEX)) {
                            lastName = next.uppercase(Locale.ROOT)
                        }
                    }
                }
            }

            // Prénom (First Name)
            if (firstName.isBlank()) {
                val prenomRegex = Regex("""(?:^|\s)(?:prénom|prenom)\s*[:=.]?\s*([A-Za-zÀ-ÖØ-öø-ÿ\s'-]+)""", RegexOption.IGNORE_CASE)
                val match = prenomRegex.find(line)
                if (match != null) {
                    val candidate = match.groupValues[1].trim()
                    if (candidate.isNotBlank() && !candidate.contains("date", ignoreCase = true) && !candidate.contains("naissance", ignoreCase = true)) {
                        firstName = formatName(candidate)
                    }
                } else {
                    // Multi-line: line is just "PRÉNOM" or "PRÉNOM :"
                    val isPrenomHeader = lower.matches(Regex("""^(?:prénom|prenom)\s*[:=.]?$"""))
                    if (isPrenomHeader && i + 1 < lines.size) {
                        val next = lines[i + 1].trim()
                        if (!isLabelLine(next) && next.matches(NAME_LINE_REGEX)) {
                            firstName = formatName(next)
                        }
                    }
                }
            }

            // Adhérent / Membre / Athlète (e.g. "Adherent: babi BOURENANE", "Judoka: Mohamed Haddad")
            if (lastName.isBlank() || firstName.isBlank()) {
                val adherentRegex = Regex("""(?:adherent|adhérent|membre|judoka|athlète)\s*[:=.]?\s*([^\n\r]+)""", RegexOption.IGNORE_CASE)
                adherentRegex.find(line)?.let {
                    val rawName = it.groupValues[1].trim()
                    if (rawName.isNotBlank() && !rawName.contains("seddouk", ignoreCase = true)) {
                        val (f, l) = parseFirstAndLastName(rawName)
                        if (firstName.isBlank()) firstName = f
                        if (lastName.isBlank()) lastName = l
                    }
                }
            }

            // Date de Naissance
            if (birthDate.isBlank()) {
                // Check if line contains date with label
                val dobLabeled = Regex("""(?i)(?:date\s*(?:de)?\s*naissance|né\(e\)\s*le|ne\(e\)\s*le|né\s*le|ne\s*le|naissance|d\.?o\.?b\.?)\s*[:=.]?\s*([0-9\s/.-]{8,15})""").find(line)
                if (dobLabeled != null) {
                    val candidate = normalizeDate(dobLabeled.groupValues[1])
                    if (candidate.isNotBlank()) birthDate = candidate
                } else {
                    // Multi-line: line is "DATE DE NAISSANCE" or "NÉ(E) LE", next line has date
                    val isDobHeader = lower.matches(Regex("""^(?:date\s*(?:de)?\s*naissance|né\(e\)\s*le|ne\(e\)\s*le|né\s*le|ne\s*le|naissance|d\.?o\.?b\.?)\s*[:=.]?$"""))
                    if (isDobHeader && i + 1 < lines.size) {
                        val next = lines[i + 1].trim()
                        val candidate = normalizeDate(next)
                        if (candidate.isNotBlank()) birthDate = candidate
                    }
                }

                // If still blank, search standard date patterns on the line (excluding season "2026/2027")
                if (birthDate.isBlank() && !lower.contains("saison") && !lower.contains("season")) {
                    val rawDateMatch = Regex("""([0-9]{4}\s*[-/.]\s*[0-9]{1,2}\s*[-/.]\s*[0-9]{1,2}|[0-9]{1,2}\s*[-/.]\s*[0-9]{1,2}\s*[-/.]\s*[0-9]{4})""").find(line)
                    if (rawDateMatch != null) {
                        val candidate = normalizeDate(rawDateMatch.groupValues[1])
                        if (candidate.isNotBlank()) birthDate = candidate
                    }
                }
            }

            // Tél. Tuteur
            if (phone.isBlank()) {
                // Match labeled phone
                val phoneLabeled = Regex("""(?i)(?:tél\.?\s*tuteur|tel\.?\s*tuteur|tuteur|contact|parent|tél\.?|tel\.?|téléphone)\s*[:=.]?\s*([0-9+\s.-]{8,18})""").find(line)
                if (phoneLabeled != null) {
                    val rawNum = cleanPhoneNumber(phoneLabeled.groupValues[1])
                    if (rawNum.isNotBlank() && rawNum != CLUB_PHONE_CLEAN) {
                        phone = rawNum
                    }
                } else {
                    // Multi-line: line is "TÉL. TUTEUR" or "TEL TUTEUR", next line is phone
                    val isPhoneHeader = lower.matches(Regex("""^(?:tél\.?\s*tuteur|tel\.?\s*tuteur|tuteur|contact|parent|tél\.?|tel\.?|téléphone)\s*[:=.]?$"""))
                    if (isPhoneHeader && i + 1 < lines.size) {
                        val next = lines[i + 1].trim()
                        val rawNum = cleanPhoneNumber(next)
                        if (rawNum.isNotBlank() && rawNum != CLUB_PHONE_CLEAN) {
                            phone = rawNum
                        }
                    }
                }

                // Generic mobile phone on line (starts with 05, 06, 07 or +213), excluding club phone
                if (phone.isBlank() && (lower.contains("tel") || lower.contains("tél") || lower.contains("tuteur") || lower.contains("contact"))) {
                    val genericMatch = Regex("""(0[567][0-9\s.-]{8,14}|\+213[0-9\s.-]{8,14})""").find(line)
                    if (genericMatch != null) {
                        val rawNum = cleanPhoneNumber(genericMatch.groupValues[1])
                        if (rawNum.isNotBlank() && rawNum != CLUB_PHONE_CLEAN) {
                            phone = rawNum
                        }
                    }
                }
            }

            // Category detection
            if (detectedCategory == null) {
                when {
                    lower.contains("baby") || lower.contains("éveil") || lower.contains("eveil") -> detectedCategory = JudoGroup.EVEIL
                    lower.contains("mini-poussin") || lower.contains("mini poussin") -> detectedCategory = JudoGroup.MINI_POUSSINS
                    lower.contains("poussin") -> detectedCategory = JudoGroup.POUSSINS
                    lower.contains("benjamin") -> detectedCategory = JudoGroup.BENJAMINS
                    lower.contains("minime") -> detectedCategory = JudoGroup.MINIMES
                    lower.contains("cadet") -> detectedCategory = JudoGroup.CADETS
                    lower.contains("junior") || lower.contains("senior") || lower.contains("sénior") -> detectedCategory = JudoGroup.SENIORS
                }
            }

            // Belt detection
            if (detectedBelt == null) {
                when {
                    lower.contains("ceinture blanche") || lower.contains("grade blanc") -> detectedBelt = Belt.BLANCHE
                    lower.contains("blanche-jaune") || lower.contains("blanche jaune") -> detectedBelt = Belt.BLANCHE_JAUNE
                    lower.contains("ceinture jaune") -> detectedBelt = Belt.JAUNE
                    lower.contains("jaune-orange") || lower.contains("jaune orange") -> detectedBelt = Belt.JAUNE_ORANGE
                    lower.contains("ceinture orange") -> detectedBelt = Belt.ORANGE
                    lower.contains("orange-verte") || lower.contains("orange verte") -> detectedBelt = Belt.ORANGE_VERTE
                    lower.contains("ceinture verte") -> detectedBelt = Belt.VERTE
                    lower.contains("ceinture bleue") -> detectedBelt = Belt.BLEUE
                    lower.contains("ceinture marron") -> detectedBelt = Belt.MARRON
                    lower.contains("ceinture noire") -> detectedBelt = Belt.NOIRE
                }
            }
        }

        // If a full name landed in the first-name slot (e.g. "Nom & Prénom: ANIS BOURENANE"),
        // split it and move the surname to the last-name slot.
        if (lastName.isBlank() && firstName.contains(' ')) {
            val (f, l) = parseFirstAndLastName(firstName)
            if (f.isNotBlank() && l.isNotBlank()) {
                firstName = f
                lastName = l
            }
        }

        // Global fallback for date across whole text if line loop missed it
        if (birthDate.isBlank()) {
            val allDates = Regex("""([0-9]{4}\s*[-/.]\s*[0-9]{1,2}\s*[-/.]\s*[0-9]{1,2}|[0-9]{1,2}\s*[-/.]\s*[0-9]{1,2}\s*[-/.]\s*[0-9]{4})""").findAll(rawText)
            for (match in allDates) {
                val candidate = normalizeDate(match.groupValues[1])
                // Avoid matching current year/season like 2026/2027
                if (candidate.isNotBlank() && !match.value.contains("2026/2027") && !match.value.contains("2025/2026")) {
                    birthDate = candidate
                    break
                }
            }
        }

        // Global fallback for phone across whole text (excluding club phone)
        if (phone.isBlank()) {
            val phoneMatches = Regex("""(?:0[567][0-9\s.-]{8,14}|\+213[0-9\s.-]{8,14})""").findAll(rawText)
            for (match in phoneMatches) {
                val clean = cleanPhoneNumber(match.value)
                if (clean.isNotBlank() && clean != CLUB_PHONE_CLEAN && clean.length in 9..14) {
                    phone = clean
                    break
                }
            }
        }

        // Global fallback for season
        if (season.isBlank()) {
            val seasonMatch = Regex("""([0-9]{4}\s*[/_-]\s*[0-9]{4})""").find(rawText)
            seasonMatch?.let {
                season = it.groupValues[1].replace(" ", "").replace("_", "/").replace("-", "/")
            }
        }

        // Global fallback for the card number. The club card prints the number as a short,
        // LABEL-LESS value in the value column, on the row right above the holder's surname
        // (e.g. "353" above "DJALI Maria"), so the OCR only ever reports a bare number and none
        // of the labelled patterns above can match it.
        if (cardNumber.isBlank()) {
            cardNumber = findBareCardNumber(lines, lastName)
        }

        // Compute group: priority to explicitly detected category, then birthDate if valid, else null/MINIMES
        val finalGroup = when {
            detectedCategory != null -> detectedCategory
            birthDate.isNotBlank() -> JudoGroup.fromBirthDate(birthDate)
            else -> JudoGroup.MINIMES
        }

        val finalBelt = detectedBelt ?: Belt.BLANCHE

        // QR Code identifier. Deliberately left blank when neither a QR code nor a card number
        // was read: inventing one here would silently give the same athlete a different
        // identifier on every scan (broken pointage, duplicate members). The editor generates a
        // stable code at save time when the field is still empty.
        val finalQrCode = when {
            !detectedQr.isNullOrBlank() -> detectedQr
            cardNumber.isNotBlank() -> "JCS-$cardNumber"
            else -> ""
        }

        val note = buildString {
            append("Carte scannée $clubName")
            if (cardNumber.isNotBlank()) append(" (N° $cardNumber)")
            if (season.isNotBlank()) append(" - Saison $season")
        }

        return ExtractedMemberInfo(
            cardNumber = cardNumber,
            firstName = firstName,
            lastName = lastName,
            birthDate = birthDate,
            phone = phone,
            season = season,
            group = finalGroup,
            belt = finalBelt,
            qrCode = finalQrCode,
            photoUrl = extractedPhotoUrl,
            clubName = clubName,
            notes = note,
            rawScannedData = rawText
        )
    }

    /**
     * Finds the card number on the layouts where it carries no readable label.
     *
     * The club cards print the number as a short standalone value in the value column, on the row
     * immediately above the holder's surname (e.g. "353" above "DJALI Maria"), so OCR reports it
     * as a bare number. Depending on how the OCR groups the table, that number may also share its
     * line with the name ("353 DJALI Maria").
     *
     * That "on the row above the name" position is the strong signal: any other short number the
     * OCR picked up (a card serial, a stray digit) is only used when nothing better was found.
     * Everything that already looks like a date, a phone number, a season or a year is ignored,
     * as is a value that merely follows a date-of-birth header (the stray day number of a
     * "NÉ(E) LE" row split across two lines).
     */
    /** A line that only carries a "5-40 characters name"-shaped value. */
    private val NAME_LINE_REGEX = Regex("""^[A-Za-zÀ-ÖØ-öø-ÿ\s'-]{2,40}$""")

    /** Words that only ever appear as printed card labels, never as a holder's name. */
    private val CARD_LABEL_WORDS = setOf(
        "nom", "noms", "prenom", "prenoms", "date", "de", "du", "des", "la", "le", "naissance",
        "ne", "nee", "e", "saison", "season", "tel", "telephone", "portable", "gsm", "tuteur",
        "pere", "mere", "carte", "licence", "cin", "sexe", "categorie", "groupe", "ceinture",
        "grade", "club", "dojo", "photo", "signature", "validite", "adherent", "adherents",
        "membre", "judoka", "athlete", "scannez", "verifier", "et", "and"
    )

    private fun stripAccents(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")

    /**
     * True when the line is nothing but card labels ("PRÉNOM", "DATE DE NAISSANCE", "NOM :").
     * The comparison is accent-insensitive: OCR returns "PRÉNOM", so an accent-sensitive check used
     * to accept the label itself as the holder's name.
     */
    private fun isLabelLine(line: String): Boolean {
        val words = stripAccents(line.lowercase(Locale.ROOT))
            .split(Regex("""[^a-z0-9]+"""))
            .filter { it.isNotBlank() }
        return words.isNotEmpty() && words.all { it in CARD_LABEL_WORDS }
    }

    private fun findBareCardNumber(lines: List<String>, lastName: String = ""): String {
        val dobHeaderRegex = Regex(
            """^(?:date\s*(?:de)?\s*naissance|né\(e\)\s*le|ne\(e\)\s*le|né\s*le|ne\s*le|naissance|d\.?o\.?b\.?)\s*[:=.]?$""",
            RegexOption.IGNORE_CASE
        )
        val dateLikeRegex = Regex("""[0-9]{1,4}\s*[-/.]\s*[0-9]{1,2}""")
        val nameOnlyRegex = Regex("""^[A-Za-zÀ-ÖØ-öø-ÿ'’\-. ]{2,40}$""")

        fun shortNumberOnItsValueRow(line: String): String? {
            val tokens = line.split(Regex("""[\s:;]+""")).filter { it.isNotBlank() }
            val head = tokens.firstOrNull().orEmpty()
            val tail = tokens.drop(1)
            val headIsShortNumber = head.length in 1..6 && head.all { it.isDigit() } &&
                !(head.length == 4 && head.toIntOrNull()?.let { it in 1900..2100 } == true)
            val tailLooksLikeName = tail.size <= 3 &&
                tail.all { token -> token.length >= 2 && token.none { c -> c.isDigit() } }
            val lineHoldsASingleValue =
                line.count { it.isDigit() } <= 6 && !dateLikeRegex.containsMatchIn(line)
            return if (headIsShortNumber && tailLooksLikeName && lineHoldsASingleValue) head else null
        }

        fun isDobHeaderAt(index: Int): Boolean =
            index in lines.indices && dobHeaderRegex.matches(lines[index].trim())

        val clubWords = listOf("judo", "club", "seddouk")
        fun isClubLine(line: String): Boolean {
            val low = line.lowercase()
            return clubWords.any { low == it || low.startsWith("$it ") || low.contains(" $it") }
        }

        val labelWords = listOf(
            "nom", "prenom", "prénom", "date", "naissance", "saison", "tel", "téléphone",
            "telephone", "adherent", "adhérent", "carte", "sexe", "categorie", "catégorie",
            "ceinture", "photo", "signature", "validite", "validité", "scan"
        )

        fun isNameValueLine(line: String): Boolean {
            if (!nameOnlyRegex.matches(line) || isClubLine(line)) return false
            val low = line.lowercase()
            return labelWords.none { word -> low == word || low.startsWith("$word ") || low.startsWith("$word:") }
        }

        fun looksLikeNameLine(line: String): Boolean = nameOnlyRegex.matches(line) && !isClubLine(line)

        // Pass 1: the number the club prints on the value row right above the surname.
        val surname = lastName.trim().replace(Regex("""\s+"""), " ").uppercase()
        if (surname.length >= 3 && !isLabelLine(surname)) {
            val surnameIndex = lines.indexOfFirst { line ->
                val upper = line.trim().uppercase()
                upper == surname ||
                    Regex("""(?<![A-Z0-9])""" + Regex.escape(surname) + """(?![A-Z0-9])""")
                        .containsMatchIn(upper)
            }
            if (surnameIndex > 0) {
                val stop = (surnameIndex - 5).coerceAtLeast(0)
                for (index in (surnameIndex - 1) downTo stop) {
                    val candidate = shortNumberOnItsValueRow(lines[index]) ?: continue
                    // A single- or two-digit value right under a birth-date header is that row's
                    // stray day number, never the card number. A three-digit value ("121") is
                    // instead a real number whose row only looks prefixed by "NAISSANCE" in
                    // ML Kit's column-wise reading of the card table.
                    if (candidate.length <= 2 && isDobHeaderAt(index - 1)) continue
                    return candidate
                }
            }
        }

        var previousLineWasDobHeader = false
        var anyOtherShortNumber = ""
        for ((index, line) in lines.withIndex()) {
            val isDobHeader = dobHeaderRegex.matches(line.trim())
            if (!previousLineWasDobHeader) {
                val candidate = shortNumberOnItsValueRow(line)
                if (candidate != null) {
                    val sharesItsLineWithTheName = line.count { it.isLetter() } >= 2
                    val sitsAboveTheName = lines.getOrNull(index + 1)?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { looksLikeNameLine(it) } == true
                    if (sharesItsLineWithTheName || sitsAboveTheName) return candidate
                    if (anyOtherShortNumber.isBlank()) anyOtherShortNumber = candidate
                }
            }
            previousLineWasDobHeader = isDobHeader
        }

        // Pass 2: on a column-wise reading the values follow the labels, so the card number is the
        // last short number printed before the holder's name block (the serials sit above it).
        val firstValueIndex = lines.indexOfFirst { isNameValueLine(it.trim()) }
        if (firstValueIndex > 0) {
            var lastAboveTheName: String? = null
            for (index in 0 until firstValueIndex) {
                shortNumberOnItsValueRow(lines[index])
                    ?.takeIf { candidate -> !(candidate.length <= 2 && isDobHeaderAt(index - 1)) }
                    ?.let { lastAboveTheName = it }
            }
            if (lastAboveTheName != null) return lastAboveTheName
        }

        return anyOtherShortNumber
    }

    private fun cleanPhoneNumber(rawPhone: String): String {
        return rawPhone.replace(" ", "").replace("-", "").replace(".", "").trim()
    }

    fun parseFirstAndLastName(fullNameRaw: String): Pair<String, String> {
        val trimmed = fullNameRaw.trim()
        if (trimmed.isBlank()) return Pair("", "")

        val parts = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return Pair("", "")
        if (parts.size == 1) return Pair(formatName(parts[0]), "")

        val upperParts = parts.filter { it.length > 1 && it.all { ch -> ch.isUpperCase() || !ch.isLetter() } }
        val nonUpperParts = parts.filterNot { it.length > 1 && it.all { ch -> ch.isUpperCase() || !ch.isLetter() } }

        return if (upperParts.isNotEmpty() && nonUpperParts.isNotEmpty()) {
            val l = upperParts.joinToString(" ")
            val f = nonUpperParts.joinToString(" ") { formatName(it) }
            Pair(f, l)
        } else {
            val f = formatName(parts.first())
            val l = parts.drop(1).joinToString(" ")
            Pair(f, l)
        }
    }

    fun formatName(name: String): String {
        return name.trim().split(Regex("\\s+")).joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }
        }
    }

    fun normalizeDate(rawDate: String): String {
        val clean = rawDate.trim()
        if (clean.isBlank()) return ""
        // Year first: YYYY MM DD (separator may be space, -, / or .).
        Regex("""(\d{4})\s*[-/.\s]\s*(\d{1,2})\s*[-/.\s]\s*(\d{1,2})""").find(clean)?.let {
            val y = it.groupValues[1].toInt()
            val mo = it.groupValues[2].toInt()
            val d = it.groupValues[3].toInt()
            if (mo in 1..12 && d in 1..31) return "%04d-%02d-%02d".format(y, mo, d)
        }
        // Day first: DD MM YYYY.
        Regex("""(\d{1,2})\s*[-/.\s]\s*(\d{1,2})\s*[-/.\s]\s*(\d{4})""").find(clean)?.let {
            val d = it.groupValues[1].toInt()
            val mo = it.groupValues[2].toInt()
            val y = it.groupValues[3].toInt()
            if (mo in 1..12 && d in 1..31 && y in 1900..2100) return "%04d-%02d-%02d".format(y, mo, d)
        }
        // No usable date found: never return a stray number (e.g. a card number read on a
        // line following a "Né(e) le" header) — return blank instead.
        return ""
    }
}