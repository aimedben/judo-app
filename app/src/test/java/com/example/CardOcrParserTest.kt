package com.example

import com.example.util.SportCardScanner
import org.junit.Assert.assertEquals
import org.junit.Test

class CardOcrParserTest {

  private fun check(
    label: String,
    raw: String,
    fn: String = "",
    ln: String = "",
    dob: String = "",
    card: String = ""
  ) {
    val r = SportCardScanner.parseCardOcrText(raw)
    assertEquals("$label firstName", fn, r.firstName)
    assertEquals("$label lastName", ln, r.lastName)
    assertEquals("$label birthDate", dob, r.birthDate)
    assertEquals("$label cardNumber", card, r.cardNumber)
  }

  @Test
  fun idealCard() = check(
    "ideal",
    "JUDO CLUB SEDDOUK\nN: 1\nAdherent: ANIS BOURENANE\nNe(e) le: 2013-01-04\nTel tuteur: 0782487924\nSaison: 2026/2027",
    fn = "Anis", ln = "BOURENANE", dob = "2013-01-04", card = "1"
  )

  @Test
  fun nameBeforeNumber() = check(
    "nameBeforeNumber",
    "JUDO CLUB SEDDOUK\nAdherent: ANIS BOURENANE\nN: 1\nNe(e) le: 2013-01-04\nTel tuteur: 0782487924",
    fn = "Anis", ln = "BOURENANE", dob = "2013-01-04", card = "1"
  )

  @Test
  fun nameStartingWithN_isNotTheCardNumber() = check(
    "nameStartingWithN",
    "JUDO CLUB SEDDOUK\nAdherent: NASSIM BOURENANE\nN: 1\nNe(e) le: 2013-01-04",
    fn = "Nassim", ln = "BOURENANE", dob = "2013-01-04", card = "1"
  )

  @Test
  fun nomPrenomLabels_areNotTheCardNumber() = check(
    "nomPrenomLabels",
    "JUDO CLUB SEDDOUK\nNom: BOURENANE\nPrenom: ANIS\nN: 1\nDate de naissance: 2013-01-04",
    fn = "Anis", ln = "BOURENANE", dob = "2013-01-04", card = "1"
  )

  @Test
  fun nomAndPrenomOnOneLine_splitsTheName() = check(
    "nomAndPrenom",
    "JUDO CLUB SEDDOUK\nNom & Prénom: ANIS BOURENANE\nN° Carte: 1\nDate de naissance: 2013-01-04",
    fn = "Anis", ln = "Bourenane", dob = "2013-01-04", card = "1"
  )

  @Test
  fun dobHeaderFollowedByStrayNumber_isNotADate() = check(
    "dobHeaderStrayNumber",
    "JUDO CLUB SEDDOUK\nAdherent: ANIS BOURENANE\nN: 1\nNé(e) le\n1\nTel tuteur: 0782487924",
    fn = "Anis", ln = "BOURENANE", dob = "", card = "1"
  )

  // --- Real club cards: the number carries no readable label -------------------------------
  // On the printed card the number is a short value in the value column, on the row right above
  // the holder's surname (e.g. "353" above "DJALI Maria"), so the OCR only ever reports a bare
  // number. These cases reproduce the layouts actually read from the tablet photos.

  @Test
  fun bareNumberAboveTheSurname_isTheCardNumber() = check(
    "bareNumberAboveSurname",
    "JUDO CLUB SEDDOUK\n353\nNOM DJALI\nPRENOM Maria\nDATE DE NAISSANCE 2008-09-18\nTEL. TUTEUR 07-92-32-1244\nSAISON 2026/2027",
    fn = "Maria", ln = "DJALI", dob = "2008-09-18", card = "353"
  )

  @Test
  fun bareNumberOnTheNameLine_isTheCardNumber() {
    // Some OCR groupings merge the value row into a single line: "353 DJALI Maria".
    val r = SportCardScanner.parseCardOcrText(
      "JUDO CLUB SEDDOUK\n353 DJALI Maria\nDATE DE NAISSANCE 2008-09-18\nTEL. TUTEUR 07-92-32-1244"
    )
    assertEquals("cardNumber", "353", r.cardNumber)
    assertEquals("qrCode", "JCS-353", r.qrCode)
  }

  @Test
  fun bareNumberWithAccentedLabels_isTheCardNumber() {
    // Same card text with the fully upper-cased accented labels printed by the club.
    val r = SportCardScanner.parseCardOcrText(
      "JUDO CLUB SEDDOUK\n307\nNOM MOHELLEBI\nPRÉNOM Amine\nDATE DE NAISSANCE 2011-02-26\n" +
        "TÉL. TUTEUR 06-72-03-30-99\nSAISON 2026/2027"
    )
    assertEquals("cardNumber", "307", r.cardNumber)
    assertEquals("qrCode", "JCS-307", r.qrCode)
    assertEquals("lastName", "MOHELLEBI", r.lastName)
  }

  // The fallback must never steal a season, a phone number or a stray day number.
  @Test
  fun seasonAndPhoneAreNeverMistakenForTheCardNumber() = check(
    "seasonAndPhone",
    "JUDO CLUB SEDDOUK\nNOM SEDDIK\nPRENOM Amine\nSAISON 2026/2027\nTEL. TUTEUR 07-92-32-1244",
    fn = "Amine", ln = "SEDDIK", dob = "", card = ""
  )

  @Test
  fun strayDayNumberAfterDobHeader_isNotTheCardNumber() = check(
    "strayDayAfterDobHeader",
    "JUDO CLUB SEDDOUK\nNOM SEDDIK\nNé(e) le\n1\nTEL. TUTEUR 07-92-32-1244",
    fn = "", ln = "SEDDIK", dob = "", card = ""
  )

  // --- No invented identifiers -------------------------------------------------------------
  // An unread number must leave BOTH the number and the QR identifier empty: generating one here
  // used to hand the same athlete a different code on every scan (broken pointage, duplicates).

  @Test
  fun unreadCardNumberLeavesNoInventedQrCode() {
    val r = SportCardScanner.parseCardOcrText(
      "JUDO CLUB SEDDOUK\nNOM SEDDIK\nPRENOM Amine\nTEL. TUTEUR 07-92-32-1244"
    )
    assertEquals("cardNumber", "", r.cardNumber)
    assertEquals("qrCode", "", r.qrCode)
  }

  @Test
  fun detectedQrCodeWinsOverTheCardNumber() {
    val r = SportCardScanner.parseCardOcrText(
      "JUDO CLUB SEDDOUK\n353\nNOM DJALI\nPRENOM Maria",
      detectedQr = "JCS-999"
    )
    assertEquals("qrCode", "JCS-999", r.qrCode)
    assertEquals("cardNumber", "353", r.cardNumber)
  }

  @Test
  fun numberAboveTheNameWinsOverALargerNumberElsewhere() {
    // A card also carries a serial (e.g. 262029) that is NOT the club card number: the number
    // printed on the row above the surname is the one the club uses, and the one the card's QR
    // reports as "N:". The serial must never take its place, whatever the reading order.
    val r = SportCardScanner.parseCardOcrText(
      "JUDO CLUB SEDDOUK\n262029\nSAISON 2026/2027\n121\nNOM IBOUZITEN\nPRENOM Damia\n" +
        "DATE DE NAISSANCE 2013-12-10\nTEL. TUTEUR 07-78-41-85-20"
    )
    assertEquals("cardNumber", "121", r.cardNumber)
    assertEquals("lastName", "IBOUZITEN", r.lastName)
    assertEquals("firstName", "Damia", r.firstName)
  }

  @Test
  fun cardNumberIsTheSameWhetherReadFromTheQrOrFromThePrintedDigits() {
    // 20261016_100835.jpg on the tablet: the QR payload and the printed digits agree on 121.
    val fromQr = SportCardScanner.parseCardOcrText(
      "JUDO CLUB SEDDOUK\nN: 121\nAdherent: Damia IBOUZITEN\nNe(e) le: 10-12-2013\n" +
        "Tel tuteur: 07-78-41-85-20\nSaison: 2026/2027"
    )
    val fromPrintedDigits = SportCardScanner.parseCardOcrText(
      "SAISON 2026/2027\nJUDO CLUB SEDDOUK\n121\nIBOUZITEN\nNOM\nDamia\nPRENOM\n" +
        "DATE DE NAISSANCE 10-12-2013\nTEL. TUTEUR 07-78-41-85-20"
    )
    assertEquals("both routes must agree", fromQr.cardNumber, fromPrintedDigits.cardNumber)
    assertEquals("121", fromPrintedDigits.cardNumber)
  }

  // --- Which barcode payload to trust ------------------------------------------------------

  private val clubQrPayload =
    "JUDO CLUB SEDDOUK\nN: 121\nAdherent: Damia IBOUZITEN\nNe(e) le: 10-12-2013\n" +
      "Tel tuteur: 07-78-41-85-20\nSaison: 2026/2027"

  @Test
  fun structuredQrPayloadOutranksABareSerialWhateverTheDetectionOrder() {
    assertEquals(clubQrPayload, SportCardScanner.chooseCardBarcodePayload(listOf("262029", clubQrPayload)))
    assertEquals(clubQrPayload, SportCardScanner.chooseCardBarcodePayload(listOf(clubQrPayload, "262029")))
  }

  @Test
  fun chooseCardBarcodePayloadKeepsWorkingForPlainCodes() {
    assertEquals("JCS-0012", SportCardScanner.chooseCardBarcodePayload(listOf("JCS-0012")))
    assertEquals("262029", SportCardScanner.chooseCardBarcodePayload(listOf("262029")))
    assertEquals(null, SportCardScanner.chooseCardBarcodePayload(emptyList()))
  }

  // --- Real tablet data (20261016_100835.jpg, tablette R9PTA0DP4DZ) -------------------------
  // The QR payload carries the club number behind a bare "N:" label, which the label regex used
  // to ignore: the number was left empty and the printed serial (262029) was stored instead.
  // The OCR text below is what ML Kit really returned for that photo, in its column-wise order.

  private val realQrPayload =
    "JUDO CLUB SEDDOUK\nN: 121\nAdherent: Damia IBOUZITEN\nNe(e) le: 10-12-2013\n" +
      "Tel tuteur: 07-78-41-85-20\nSaison: 2026/2027"

  private val realMlKitText =
    "262029\n126\nJUDO CLUB SEDDOUK\njudoclubseddouk@gmail.com\nNOM\nPRÉNOM\nDATE DE\n" +
      "NAISSANCE\n121\n0794-33-50-10CSP Seddouk, Béjaïa\nIBOUZITEN\nDamia\n10-12-2013\n" +
      "TÉL. TUTEUR 07-78-41-85-20\nSAISON 2026/2027\nScannez\nvériier"

  @Test
  fun qrPayloadReadsTheClubNumberBehindABareNLabel() {
    val parsed = com.example.util.CardDataParser.parseCardData(realQrPayload)
    assertEquals("cardNumber", "121", parsed.cardNumber)
    assertEquals("qrCode", "JCS-121", parsed.qrCode)
    assertEquals("firstName", "Damia", parsed.firstName)
    assertEquals("lastName", "Ibouziten", parsed.lastName)
    assertEquals("birthDate", "2013-12-10", parsed.birthDate)
    assertEquals("phone", "0778418520", parsed.phone)
  }

  @Test
  fun realTabletScanKeepsTheClubNumberNotThePrintedSerial() {
    val ocr = SportCardScanner.parseCardOcrText(realMlKitText, detectedQr = realQrPayload)
    val merged = SportCardScanner.mergeQrAndOcr(
      qrParsed = com.example.util.CardDataParser.parseCardData(realQrPayload),
      ocrParsed = ocr,
      qrCodeValue = realQrPayload,
      photoUriString = "",
      rawText = realMlKitText
    )
    assertEquals("cardNumber", "121", merged.cardNumber)
    // Same identifier the douchette reads from the printed QR, so the card stays scannable.
    assertEquals("qrCode", "JCS-121", merged.qrCode)
    assertEquals("lastName", "Ibouziten", merged.lastName)
    assertEquals("firstName", "Damia", merged.firstName)
    assertEquals("birthDate", "2013-12-10", merged.birthDate)
    assertEquals("phone", "0778418520", merged.phone)
  }

  @Test
  fun withoutAnyReadableBarcodeTheNumberAboveTheNameBlockIsUsed() {
    // Damaged QR: neither barcode nor surname label survives, only the printed digits.
    val r = SportCardScanner.parseCardOcrText(realMlKitText)
    assertEquals("cardNumber", "121", r.cardNumber)
  }

  @Test
  fun thePrintedSerialIsNeverTheCardNumber() {
    val r = SportCardScanner.parseCardOcrText(
      "262029\nJUDO CLUB SEDDOUK\njudoclubseddouk@gmail.com\nNOM\nPRÉNOM\n121\nIBOUZITEN\nDamia"
    )
    assertEquals("cardNumber", "121", r.cardNumber)
  }

}
