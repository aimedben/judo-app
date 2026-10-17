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
}
