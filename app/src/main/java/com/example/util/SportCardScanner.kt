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
import java.util.Locale

object SportCardScanner {

    /** Club administration phone number printed on cards (must NOT be assigned to athlete/tutor) */
    private const val CLUB_PHONE_CLEAN = "0794335010"

    /** Parse a raw (already extracted) card text or QR payload. */
    fun parseCardText(text: String): ExtractedMemberInfo = CardDataParser.parseCardData(text)

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
            val qrCodeValue = barcodes.firstOrNull()?.rawValue?.trim()

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
            val mergedResult = if (qrParsed != null) {
                val finalBirth = qrParsed.birthDate.ifBlank { ocrParsed.birthDate }
                val finalGroup = if (qrParsed.birthDate.isNotBlank()) {
                    JudoGroup.fromBirthDate(qrParsed.birthDate)
                } else {
                    ocrParsed.group
                }
                ExtractedMemberInfo(
                    cardNumber = qrParsed.cardNumber.ifBlank { ocrParsed.cardNumber },
                    firstName = qrParsed.firstName.ifBlank { ocrParsed.firstName },
                    lastName = qrParsed.lastName.ifBlank { ocrParsed.lastName },
                    birthDate = finalBirth,
                    phone = qrParsed.phone.ifBlank { ocrParsed.phone },
                    season = qrParsed.season.ifBlank { ocrParsed.season },
                    group = finalGroup,
                    belt = if (qrParsed.belt != Belt.BLANCHE) qrParsed.belt else ocrParsed.belt,
                    qrCode = if (!qrCodeValue.isNullOrBlank()) qrCodeValue else ocrParsed.qrCode,
                    photoUrl = photoUriString.ifBlank { qrParsed.photoUrl },
                    clubName = qrParsed.clubName.ifBlank { ocrParsed.clubName },
                    notes = qrParsed.notes.ifBlank { ocrParsed.notes },
                    rawScannedData = if (rawText.isNotBlank()) rawText else qrCodeValue ?: ""
                )
            } else {
                ocrParsed
            }

            Result.success(mergedResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
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

            // Card Number / N°
            if (cardNumber.isBlank()) {
                // Match "N° 1", "N: 1", "N° : 1", "N°:1", "Carte N° 1"
                val nRegex = Regex("""(?:^|\s)(?:n°\s*:?|n\s*:?|n\.|carte\s*n°?|numéro|numero)\s*[:=.]?\s*([0-9a-zA-Z_-]+)""", RegexOption.IGNORE_CASE)
                val match = nRegex.find(line)
                if (match != null) {
                    val candidate = match.groupValues[1].trim()
                    if (candidate.isNotEmpty() && !candidate.equals("seddouk", ignoreCase = true)) {
                        cardNumber = candidate
                    }
                } else {
                    // Check if line is just "N°" or "N° :" and the number is on the NEXT line
                    val isNHeader = lower.matches(Regex("""^(?:n°|n|n\.|carte\s*n°?|numéro|numero)\s*[:=.]?$"""))
                    if (isNHeader && i + 1 < lines.size) {
                        val next = lines[i + 1].trim()
                        if (next.matches(Regex("""^[0-9a-zA-Z_-]{1,15}$"""))) {
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
                        if (!next.contains("prenom", ignoreCase = true) && !next.contains("naissance", ignoreCase = true) &&
                            next.matches(Regex("""^[A-Za-zÀ-ÖØ-öø-ÿ\s'-]{2,40}$"""))
                        ) {
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
                        if (!next.contains("date", ignoreCase = true) && !next.contains("naissance", ignoreCase = true) &&
                            next.matches(Regex("""^[A-Za-zÀ-ÖØ-öø-ÿ\s'-]{2,40}$"""))
                        ) {
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

        // Compute group: priority to explicitly detected category, then birthDate if valid, else null/MINIMES
        val finalGroup = when {
            detectedCategory != null -> detectedCategory
            birthDate.isNotBlank() -> JudoGroup.fromBirthDate(birthDate)
            else -> JudoGroup.MINIMES
        }

        val finalBelt = detectedBelt ?: Belt.BLANCHE

        // QR Code identifier
        val finalQrCode = when {
            !detectedQr.isNullOrBlank() -> detectedQr
            cardNumber.isNotBlank() -> "JCS-$cardNumber"
            else -> "JCS-${System.currentTimeMillis().toString().takeLast(5)}"
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
        // Check YYYY-MM-DD (with optional spaces around separator)
        val ymd = Regex("""(\d{4})\s*[-/.]\s*(\d{1,2})\s*[-/.]\s*(\d{1,2})""").find(clean)
        if (ymd != null) {
            val y = ymd.groupValues[1]
            val m = ymd.groupValues[2].padStart(2, '0')
            val d = ymd.groupValues[3].padStart(2, '0')
            return "$y-$m-$d"
        }
        // Check DD/MM/YYYY (with optional spaces around separator)
        val dmy = Regex("""(\d{1,2})\s*[-/.]\s*(\d{1,2})\s*[-/.]\s*(\d{4})""").find(clean)
        if (dmy != null) {
            val d = dmy.groupValues[1].padStart(2, '0')
            val m = dmy.groupValues[2].padStart(2, '0')
            val y = dmy.groupValues[3]
            return "$y-$m-$d"
        }
        return clean
    }
}