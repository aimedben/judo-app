package com.example

import com.example.data.model.ActiveTrainingSession
import com.example.data.model.JudoCategory
import com.example.util.SessionScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the invariant "a running session only belongs to the category it was started for".
 *
 * Regression: leaving a session running while browsing the category selection made every category
 * card open the pointage of that same session (all cards appeared to lead to the same category).
 */
class SessionScopeTest {

    private fun category(id: String, groupCode: String = "U15"): JudoCategory = JudoCategory(
        id = id,
        name = id,
        groupCode = groupCode,
        coach1Name = "Sensei",
        coach2Name = "Coach"
    )

    private fun session(categoryId: String): ActiveTrainingSession = ActiveTrainingSession(
        categoryId = categoryId,
        categoryName = categoryId,
        startTime = "18:00",
        startDate = "2026-10-17",
        coach1Name = "Sensei",
        coach1Present = true,
        coach2Name = "Coach",
        coach2Present = false,
        startTimestamp = 1_700_000_000_000L
    )

    @Test
    fun `no session means nothing to display`() {
        assertNull(SessionScope.forCategory(null, category("minimes")))
        assertFalse(SessionScope.isActive(null, category("minimes")))
    }

    @Test
    fun `no selected category means the session must not be displayed`() {
        assertNull(SessionScope.forCategory(session("minimes"), null))
        assertFalse(SessionScope.isActive(session("minimes"), null))
    }

    @Test
    fun `session is returned for its own category`() {
        val minimes = session("minimes")
        assertEquals(minimes, SessionScope.forCategory(minimes, category("minimes")))
        assertTrue(SessionScope.isActive(minimes, category("minimes")))
    }

    @Test
    fun `session from another category is never displayed`() {
        val minimes = session("minimes")
        assertNull(SessionScope.forCategory(minimes, category("cadets")))
        assertFalse(SessionScope.isActive(minimes, category("cadets")))
    }

    @Test
    fun `categories are matched by id not by group code`() {
        // Two distinct categories sharing a group code must not exchange their session.
        val minimes = session("minimes")
        val otherWithSameGroup = category("minimes-bis", groupCode = "U15")
        assertNull(SessionScope.forCategory(minimes, otherWithSameGroup))
        assertFalse(SessionScope.isActive(minimes, otherWithSameGroup))
    }

    @Test
    fun `every category card of the selection screen keeps its own session`() {
        val ids = listOf("baby", "adultes", "poussins", "benjamins", "minimes", "cadets")
        val running = session("minimes")
        ids.forEach { id ->
            val resolved = SessionScope.forCategory(running, category(id))
            if (id == "minimes") {
                assertEquals(running, resolved)
            } else {
                assertNull("La séance Minimes ne doit pas s'afficher pour $id", resolved)
            }
        }
    }
}
