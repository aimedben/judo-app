package com.example

import com.example.data.model.Member
import com.example.util.AbsentSmsNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the SMS-to-guardians-of-absent-athletes feature.
 *
 * They cover: identifying the absent athletes, excluding present ones (handled by the caller, so we
 * assert only the passed-in athletes are used), missing / invalid / duplicated numbers, message
 * personalisation, the "no absent" case and the guarantee that no message is ever declared sent.
 */
class AbsentSmsNotifierTest {

    private var nextId = 1L

    private fun member(
        firstName: String,
        lastName: String,
        phone: String,
        groupCode: String = "U15",
        id: Long = 0L
    ): Member {
        val resolvedId = if (id == 0L) nextId++ else id
        return Member(
            id = resolvedId,
            qrCode = "JS-2026-%04d".format(resolvedId),
            firstName = firstName,
            lastName = lastName,
            birthDate = "2013-01-04",
            phone = phone,
            groupCode = groupCode,
            beltName = "VERTE"
        )
    }

    // ------------------------------------------------------------------ absent identification

    @Test
    fun `builds one recipient per absent athlete with a valid number`() {
        val plan = AbsentSmsNotifier.buildPlan(
            listOf(
                member("Anis", "BOURENANE", "0782487924"),
                member("Nour", "HAMADI", "0661 44 33 22")
            )
        )

        assertEquals(2, plan.absentCount)
        assertEquals(2, plan.validNumberCount)
        assertEquals(2, plan.messageCount)
        assertTrue(plan.hasRecipients)
        assertEquals(listOf("Anis BOURENANE", "Nour HAMADI"), plan.recipients.map { it.memberName })
    }

    @Test
    fun `an empty absent list produces no message and no recipient`() {
        val plan = AbsentSmsNotifier.buildPlan(emptyList())

        assertEquals(0, plan.absentCount)
        assertEquals(0, plan.messageCount)
        assertFalse(plan.hasRecipients)
        assertTrue(plan.skipped.isEmpty())
        assertTrue(plan.sharedNumbers.isEmpty())
    }

    // ------------------------------------------------------- missing / invalid / duplicate numbers

    @Test
    fun `a blank number is reported as missing, not invalid`() {
        val plan = AbsentSmsNotifier.buildPlan(
            listOf(
                member("Sans", "NUMERO", ""),
                member("Avec", "NUMERO", "0782487924")
            )
        )

        assertEquals(1, plan.validNumberCount)
        assertEquals(listOf("Sans NUMERO"), plan.skippedByReason(AbsentSmsNotifier.SkipReason.MISSING_PHONE).map { it.memberName })
        assertTrue(plan.skippedByReason(AbsentSmsNotifier.SkipReason.INVALID_PHONE).isEmpty())
    }

    @Test
    fun `a garbage number is reported as invalid`() {
        val plan = AbsentSmsNotifier.buildPlan(
            listOf(
                member("Faux", "NUMERO", "12"),
                member("Texte", "SEUL", "abcdef"),
                member("Bon", "NUMERO", "0771990022")
            )
        )

        assertEquals(1, plan.validNumberCount)
        val invalid = plan.skippedByReason(AbsentSmsNotifier.SkipReason.INVALID_PHONE).map { it.memberName }
        assertEquals(listOf("Faux NUMERO", "Texte SEUL"), invalid)
    }

    @Test
    fun `the same athlete listed twice is excluded as a duplicate`() {
        val duplicated = member("Anis", "BOURENANE", "0782487924", id = 7L)
        val plan = AbsentSmsNotifier.buildPlan(listOf(duplicated, duplicated))

        assertEquals(1, plan.validNumberCount)
        // The raw analysed list contained two entries, but only one distinct athlete will be contacted.
        assertEquals(2, plan.absentCount)
        assertEquals(1, plan.messageCount)
        assertEquals(
            listOf("Anis BOURENANE"),
            plan.skippedByReason(AbsentSmsNotifier.SkipReason.DUPLICATE_MEMBER).map { it.memberName }
        )
    }

    @Test
    fun `two siblings sharing the same guardian number are both kept and the number is flagged`() {
        val plan = AbsentSmsNotifier.buildPlan(
            listOf(
                member("Anis", "BOURENANE", "0782487924"),
                member("Nassim", "BOURENANE", "+213 782 48 79 24")
            )
        )

        // Both children still receive their own personalised message…
        assertEquals(2, plan.validNumberCount)
        // …but the shared guardian number is surfaced so the coach is warned.
        assertEquals(listOf("0782487924"), plan.sharedNumbers)
    }

    @Test
    fun `distinct numbers are never reported as shared`() {
        val plan = AbsentSmsNotifier.buildPlan(
            listOf(
                member("Anis", "BOURENANE", "0782487924"),
                member("Nour", "HAMADI", "0661443322")
            )
        )

        assertTrue(plan.sharedNumbers.isEmpty())
    }

    // ---------------------------------------------------------------------- phone normalisation

    @Test
    fun `normalises the formats stored by the club`() {
        assertEquals("0782487924", AbsentSmsNotifier.normalizePhone("0782487924"))
        assertEquals("0553447711", AbsentSmsNotifier.normalizePhone("0553 44 77 11"))
        assertEquals("0553447711", AbsentSmsNotifier.normalizePhone("0553.44.77.11"))
        assertEquals("0553447711", AbsentSmsNotifier.normalizePhone("0553-44-77-11"))
        assertEquals("0782487924", AbsentSmsNotifier.normalizePhone("+213 782 48 79 24"))
        assertEquals("0782487924", AbsentSmsNotifier.normalizePhone("00213782487924"))
        assertEquals("0782487924", AbsentSmsNotifier.normalizePhone("213782487924"))
    }

    @Test
    fun `rejects unusable numbers`() {
        assertNull(AbsentSmsNotifier.normalizePhone(null))
        assertNull(AbsentSmsNotifier.normalizePhone(""))
        assertNull(AbsentSmsNotifier.normalizePhone("   "))
        assertNull(AbsentSmsNotifier.normalizePhone("abcdef"))
        assertNull(AbsentSmsNotifier.normalizePhone("12"))
        assertNull(AbsentSmsNotifier.normalizePhone("1234567890123456"))
        assertNull(AbsentSmsNotifier.normalizePhone("0000000000"))
        assertNull(AbsentSmsNotifier.normalizePhone("1111111111"))
    }

    @Test
    fun `sms uri number keeps digits only`() {
        assertEquals("0782487924", AbsentSmsNotifier.toSmsUriNumber("0782487924"))
        assertEquals("0782487924", AbsentSmsNotifier.toSmsUriNumber("078 248 79 24"))
        // Composed with normalisation (the intended pipeline): the international form becomes national.
        val normalized = AbsentSmsNotifier.normalizePhone("+213 782 48 79 24")!!
        assertEquals("0782487924", AbsentSmsNotifier.toSmsUriNumber(normalized))
    }

    // ------------------------------------------------------------------------ message templating

    @Test
    fun `message is personalised with the child name and the session date`() {
        val message = AbsentSmsNotifier.buildMessage(
            memberName = "Anis BOURENANE",
            sessionDate = "2026-10-17",
            clubName = "JUDO CLUB SEDDOUK"
        )

        assertTrue(message.contains("Anis BOURENANE"))
        assertTrue(message.contains("samedi 17 octobre 2026"))
        assertTrue(message.contains("JUDO CLUB SEDDOUK"))
    }

    @Test
    fun `message falls back to a generic wording when the club name is missing`() {
        val message = AbsentSmsNotifier.buildMessage(
            memberName = "Anis BOURENANE",
            sessionDate = "2026-10-17",
            clubName = "  "
        )

        assertTrue(message.contains("contacter le club."))
        assertFalse(message.contains("()"))
    }

    @Test
    fun `message keeps the raw date when it cannot be parsed`() {
        val message = AbsentSmsNotifier.buildMessage(
            memberName = "Anis",
            sessionDate = "date inconnue",
            clubName = null
        )

        assertTrue(message.contains("date inconnue"))
    }

    @Test
    fun `message lowercases a legacy capitalised date so it reads mid-sentence`() {
        val message = AbsentSmsNotifier.buildMessage("Anis", "Samedi 4 Octobre 2026", null)
        assertTrue(message.contains("du samedi 4 Octobre 2026"))
        assertFalse(message.contains("du Samedi"))
    }

    @Test
    fun `message keeps the raw date when the date is invalid`() {
        val message = AbsentSmsNotifier.buildMessage("Anis", "2026-13-45", null)
        assertTrue(message.contains("2026-13-45"))
    }

    @Test
    fun `formats an iso date into a french label`() {
        assertEquals("samedi 17 octobre 2026", AbsentSmsNotifier.formatSessionDate("2026-10-17"))
        assertEquals("lundi 5 janvier 2026", AbsentSmsNotifier.formatSessionDate("2026-01-05"))
    }

    @Test
    fun `returns the raw value when the date is not iso`() {
        assertEquals("17/10/2026", AbsentSmsNotifier.formatSessionDate("17/10/2026"))
        assertEquals("", AbsentSmsNotifier.formatSessionDate(""))
    }

    // ------------------------------------------------------------------- resolving stored names

    @Test
    fun `resolves recorded absent names to category members`() {
        val roster = listOf(
            member("Anis", "BOURENANE", "0782487924"),
            member("Nour", "HAMADI", "0661443322"),
            member("Yacine", "TOUATI", "0771990022")
        )
        val resolved = AbsentSmsNotifier.resolveAbsentMembers(
            absentNames = listOf("Nour HAMADI", "Yacine TOUATI"),
            categoryMembers = roster
        )

        assertEquals(listOf("Nour HAMADI", "Yacine TOUATI"), resolved.map { it.fullName })
    }

    @Test
    fun `resolving ignores names that no longer match a member`() {
        val roster = listOf(member("Anis", "BOURENANE", "0782487924"))
        val resolved = AbsentSmsNotifier.resolveAbsentMembers(
            absentNames = listOf("Ancien PARTI", "Anis BOURENANE"),
            categoryMembers = roster
        )

        assertEquals(listOf("Anis BOURENANE"), resolved.map { it.fullName })
    }

    @Test
    fun `resolving is case and whitespace insensitive`() {
        val roster = listOf(member("Anis", "BOURENANE", "0782487924"))
        val resolved = AbsentSmsNotifier.resolveAbsentMembers(
            absentNames = listOf("  anis bourenane "),
            categoryMembers = roster
        )

        assertEquals(1, resolved.size)
    }

    @Test
    fun `resolving an empty absent list yields nothing`() {
        val roster = listOf(member("Anis", "BOURENANE", "0782487924"))
        assertTrue(AbsentSmsNotifier.resolveAbsentMembers(emptyList(), roster).isEmpty())
        assertTrue(AbsentSmsNotifier.resolveAbsentMembers(listOf("Anis BOURENANE"), emptyList()).isEmpty())
    }

    @Test
    fun `filters members of a group ignoring case`() {
        val roster = listOf(
            member("Anis", "BOURENANE", "0782487924", groupCode = "U15"),
            member("Nour", "HAMADI", "0661443322", groupCode = "U13")
        )

        assertEquals(
            listOf("Anis BOURENANE"),
            AbsentSmsNotifier.membersOfGroup(roster, "u15").map { it.fullName }
        )
        assertTrue(AbsentSmsNotifier.membersOfGroup(roster, null).isEmpty())
        assertTrue(AbsentSmsNotifier.membersOfGroup(roster, "").isEmpty())
    }

    // --------------------------------------------------- never mark a message as sent (no SIM ack)

    @Test
    fun `every recipient starts as not prepared`() {
        val plan = AbsentSmsNotifier.buildPlan(listOf(member("Anis", "BOURENANE", "0782487924")))
        val states = AbsentSmsNotifier.initialDeliveryStates(plan)

        assertEquals(1, states.size)
        assertTrue(states.values.all { it == AbsentSmsNotifier.SmsDeliveryState.NOT_PREPARED })
    }

    @Test
    fun `there is no sent state because android cannot confirm sms delivery`() {
        val names = AbsentSmsNotifier.SmsDeliveryState.values().map { it.name }
        assertFalse("A 'SENT' state must not exist: ACTION_SENDTO cannot confirm delivery", names.any { it.contains("SENT") })
    }

    // -------------------------------------------------------------------------------- robustness

    @Test
    fun `building a plan does not mutate the input list`() {
        val input = listOf(member("Anis", "BOURENANE", "0782487924"))
        val copy = input.toList()

        AbsentSmsNotifier.buildPlan(input)

        assertEquals(copy, input)
    }

    @Test
    fun `building the same plan twice is idempotent`() {
        val input = listOf(
            member("Anis", "BOURENANE", "0782487924"),
            member("Sans", "NUMERO", "")
        )

        val first = AbsentSmsNotifier.buildPlan(input)
        val second = AbsentSmsNotifier.buildPlan(input)

        assertEquals(first.recipients, second.recipients)
        assertEquals(first.skipped, second.skipped)
        assertEquals(first.sharedNumbers, second.sharedNumbers)
    }
}
