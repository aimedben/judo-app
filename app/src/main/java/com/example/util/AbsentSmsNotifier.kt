package com.example.util

import com.example.data.model.Member
import java.util.Locale

/**
 * Pure, Android-free logic behind the "SMS aux parents des absents" feature.
 *
 * Everything here is deliberately side-effect free so it can be unit tested without a device:
 * identifying the absent athletes, normalising / validating the parent phone numbers, detecting
 * duplicates and building the personalised message.
 *
 * The actual dispatch (opening the native SMS application through `Intent.ACTION_SENDTO`) lives in
 * [com.example.ui.components.AbsentSmsDialog] so that this object stays testable.
 *
 * IMPORTANT: this object only **prepares** messages. It never marks a message as sent — Android's
 * `ACTION_SENDTO` hands the message over to the user's SMS application and does not report back
 * whether it was actually sent.
 */
object AbsentSmsNotifier {

    /** Club label used when the club name is not configured (fallback). */
    const val DEFAULT_CLUB_LABEL = "Judo Club Seddouk"

    /** Why a given absent athlete was left out of the SMS preparation. */
    enum class SkipReason {
        /** No phone number registered for the guardian. */
        MISSING_PHONE,

        /** A number is present but does not look like a usable phone number. */
        INVALID_PHONE,

        /** The same athlete appears twice in the source list. */
        DUPLICATE_MEMBER
    }

    /** A single SMS that can be prepared for one guardian. */
    data class SmsRecipient(
        val memberId: Long,
        val memberName: String,
        /** The raw value stored in the database (kept for display / verification). */
        val rawPhone: String,
        /** The cleaned number actually used in the `smsto:` URI. */
        val normalizedPhone: String
    )

    /** An absent athlete that could not be turned into a recipient, with the reason why. */
    data class SmsSkip(
        val memberName: String,
        val reason: SkipReason
    )

    /**
     * Result of analysing the absent athletes of a session.
     *
     * @param recipients       one entry per distinct absent athlete with a valid number.
     * @param skipped          absent athletes that were excluded, with the reason.
     * @param sharedNumbers    normalised numbers used by more than one recipient (for example
     *                         siblings whose guardian is the same person). These are **not**
     *                         excluded — each child still gets their own personalised message —
     *                         but they are surfaced so the coach is warned.
     * @param absentCount      total number of absent athletes analysed.
     */
    data class SmsPlan(
        val recipients: List<SmsRecipient>,
        val skipped: List<SmsSkip>,
        val sharedNumbers: List<String>,
        val absentCount: Int
    ) {
        val validNumberCount: Int get() = recipients.size
        val messageCount: Int get() = recipients.size
        val hasRecipients: Boolean get() = recipients.isNotEmpty()

        /** Excluded entries keyed by reason (used by the recap UI). */
        fun skippedByReason(reason: SkipReason): List<SmsSkip> = skipped.filter { it.reason == reason }
    }

    /**
     * Builds the sending plan from the athletes **absent from a session**.
     *
     * IMPORTANT: the caller must only pass athletes belonging to the category the session was
     * held for, minus those actually present. This object does not know about attendance.
     *
     * Ordering of the exclusion rules: missing number → invalid number → duplicate athlete.
     */
    fun buildPlan(absentMembers: List<Member>): SmsPlan {        val recipients = mutableListOf<SmsRecipient>()
        val skipped = mutableListOf<SmsSkip>()
        val seenMemberIds = mutableSetOf<Long>()
        val numberUsage = mutableMapOf<String, Int>()

        for (member in absentMembers) {
            val rawPhone = member.phone.orEmpty()
            val normalized = normalizePhone(rawPhone)

            when {
                !seenMemberIds.add(member.id) ->
                    skipped.add(SmsSkip(member.fullName, SkipReason.DUPLICATE_MEMBER))

                rawPhone.isBlank() ->
                    skipped.add(SmsSkip(member.fullName, SkipReason.MISSING_PHONE))

                normalized == null ->
                    skipped.add(SmsSkip(member.fullName, SkipReason.INVALID_PHONE))

                else -> {
                    recipients.add(
                        SmsRecipient(
                            memberId = member.id,
                            memberName = member.fullName,
                            rawPhone = rawPhone,
                            normalizedPhone = normalized
                        )
                    )
                    numberUsage[normalized] = (numberUsage[normalized] ?: 0) + 1
                }
            }
        }

        val sharedNumbers = numberUsage
            .filterValues { it > 1 }
            .keys
            .sorted()

        return SmsPlan(
            recipients = recipients,
            skipped = skipped,
            sharedNumbers = sharedNumbers,
            absentCount = absentMembers.size
        )
    }

    /**
     * Enriches a recorded list of absent athlete **names** with the actual [Member] objects, so the
     * guardian phone numbers become available.
     *
     * A finished session only stores the names of the absent athletes; the consultation screens
     * (admin history, coach history) therefore resolve them against the category roster. Names that
     * no longer match a member of the category are ignored — no phone number could be reached for
     * them anyway.
     *
     * Matching is done on the full name, trimmed and case-insensitive.
     */
    fun resolveAbsentMembers(
        absentNames: List<String>,
        categoryMembers: List<Member>
    ): List<Member> {
        if (absentNames.isEmpty() || categoryMembers.isEmpty()) return emptyList()
        val wanted = absentNames.map { it.trim().lowercase(Locale.ROOT) }.toHashSet()
        return categoryMembers.filter { it.fullName.trim().lowercase(Locale.ROOT) in wanted }
    }

    /**
     * Members of a category, matched on the category's group code (case-insensitive).
     * A category can cover several groups: "Cadets, Juniors & Seniors" stores "U18,SENIOR".
     */
    fun membersOfGroup(members: List<Member>, groupCode: String?): List<Member> {
        if (groupCode.isNullOrBlank()) return emptyList()
        return members.filter { GroupCodes.matches(groupCode, it.groupCode) }
    }

    /**
     * Normalises a phone number so it can be used in a `smsto:` URI and compared for duplicates.
     *
     * Supports the formats found in the club's data (Algeria, country code +213):
     *  - `"0553 44 77 11"`, `"0553.44.77.11"`, `"0553-44-77-11"`
     *  - `"+213553447711"`, `"00213553447711"`, `"213553447711"`
     *
     * @return the national form (`0XXXXXXXXX`) or null when the value is not a usable number.
     */
    fun normalizePhone(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        var text = raw.trim()
        val hasPlus = text.startsWith("+")
        // Keep digits only (drops spaces, dashes, dots, parentheses, slashes, letters).
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return null

        val national = when {
            // +213... / 00213... / 213... → local 0...
            digits.startsWith("00213") -> "0" + digits.removePrefix("00213")
            hasPlus && digits.startsWith("213") -> "0" + digits.removePrefix("213")
            digits.length == 12 && digits.startsWith("213") -> "0" + digits.removePrefix("213")
            else -> digits
        }

        // Algerian valid numbers: 10 digits for mobiles (0X XX XX XX XX) and 9 for landlines.
        if (national.length !in 9..10) return null
        if (!national.startsWith("0")) return null

        // Reject obviously fake values made of a single repeated digit (e.g. "0000000000").
        if (national.all { it == national[0] }) return null

        return national
    }

    /**
     * Formats the session start time ("17:30") into the "17h30" wording of the message.
     * Null when the session has no start time.
     */
    fun formatSessionClock(start: String?): String? {
        val value = start?.trim().orEmpty()
        val match = Regex("^(\\d{1,2})[:hH.](\\d{2})$").find(value) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        return if (hour in 0..23) "${match.groupValues[1]}h${match.groupValues[2]}" else null
    }

    /**
     * Builds the personalised message for one guardian.
     *
     * Official club template; the session date (dd/MM/yyyy) and time (HHhMM) are recovered
     * automatically from the session. The athlete name is always the one of the child whose
     * number is being messaged: each recipient gets their own message built from their own
     * absent athlete — never from another athlete's name.
     *
     * Fallbacks: an unparseable date keeps its raw value, a missing start time is omitted,
     * and a blank [clubName] falls back to the club's default label.
     */
    fun buildMessage(
        memberName: String,
        sessionDate: String,
        sessionStart: String? = null,
        clubName: String? = null
    ): String {
        val name = memberName.trim().ifBlank { "votre enfant" }
        // Slash date (dd/MM/yyyy), as written on the club's letter.
        val dateLabel = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(sessionDate.trim())
            ?.let { "${it.groupValues[3]}/${it.groupValues[2]}/${it.groupValues[1]}" }
            ?: sessionDate.trim()
        val clockLabel = formatSessionClock(sessionStart)?.let { " à $it" }.orEmpty()
        val club = clubName?.trim().orEmpty().ifBlank { DEFAULT_CLUB_LABEL }

        return "Bonjour,\n" +
            "Madame, Monsieur,\n" +
            "Nous vous informons que votre enfant $name ne s'est pas présenté(e) à la séance " +
            "d'entraînement de ce jour le $dateLabel$clockLabel et, par conséquent, " +
            "n'a pas été enregistré(e) sur notre système de pointage.\n" +
            "Cordialement,\n" +
            "L'administration du $club"
    }

    /** Transport format used by the `smsto:` URI (keeps the national form, digits only). */
    fun toSmsUriNumber(normalizedPhone: String): String = normalizedPhone.filter { it.isDigit() }

    /**
     * Delivery state reachable for a prepared message.
     *
     * NOTE: there is intentionally **no `SENT` state**. `Intent.ACTION_SENDTO` only hands the
     * message to the user's SMS application; Android never reports back whether the message was
     * really sent, so the application must not claim that it was.
     */
    enum class SmsDeliveryState {
        /** Nothing has been prepared yet for this guardian. */
        NOT_PREPARED,

        /** The SMS application was opened with this message prefilled (send not confirmed). */
        PREPARED_IN_SMS_APP
    }

    /** Initial (nothing prepared yet) delivery state for every recipient of [plan]. */
    fun initialDeliveryStates(plan: SmsPlan): Map<Long, SmsDeliveryState> =
        plan.recipients.associate { it.memberId to SmsDeliveryState.NOT_PREPARED }
}
