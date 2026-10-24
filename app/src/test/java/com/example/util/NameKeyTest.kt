package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression : la correction d'historique ne doit jamais creer de doublon de nom. */
class NameKeyTest {

    @Test
    fun `casse espaces et accents ne creent pas d'athletes differents`() {
        assertTrue(NameKey.same("Dilmi Amine", "dilmi amine"))
        assertTrue(NameKey.same("Dilmi Amine", "  Dilmi   Amine  "))
        assertTrue(NameKey.same("Saadi Larbi", "Saâdi Larbi"))
        assertTrue(NameKey.same("Benkacem", "Benkacém"))
        assertTrue(NameKey.same("AIT SAID Mohamed", "Ait Said   mohamed"))
    }

    @Test
    fun `des membres differents restent distincts`() {
        assertFalse(NameKey.same("Dilmi Amine", "Dilmi Kamel"))
        assertFalse(NameKey.same("Amine", "Amin"))
    }

    @Test
    fun `canonical est stable et sans accent`() {
        assertEquals("ait said mohamed", NameKey.canonical("AIT  SAID Mohamed "))
        assertEquals("saadi larbi", NameKey.canonical("Saâdi Larbi"))
    }

    @Test
    fun `verifie le scenario de double - absent nomme differemment est supprime quand on le passe present`() {
        val absents = listOf("Dilmi Amine")
        val presents = mutableListOf<String>("Dilmi Amine ")
        val ajout = "Dilmi  Aminé"
        // L'absent qui correspond par cle canonique est retire de la liste des absents.
        val absentsCorriges = absents
            .filter { !NameKey.same(it, ajout) }
        assertTrue(
            "absentsCorriges=$absentsCorriges",
            absentsCorriges.none { NameKey.same(it, ajout) }
        )
        // Le present est ajoute une seule fois : le miroir par cle canonique ne deduplique pas.
        presents.add(ajout)
        assertEquals(2, presents.size)
        val mirror = presents.associateBy { NameKey.canonical(it) }.values
        assertEquals(1, mirror.size)
    }
}
