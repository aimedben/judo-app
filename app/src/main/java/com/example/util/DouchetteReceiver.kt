package com.example.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Receives the character stream produced by a USB / Bluetooth HID barcode scanner
 * ("douchette") and delivers exactly ONE identifier per physical read.
 *
 * The club card QR is a multi-line payload:
 *
 *     JUDO CLUB SEDDOUK
 *     N: 121
 *     Adherent: Damia IBOUZITEN
 *     ...
 *
 * The identifier sits on the second line, so the read is delivered as soon as that line is
 * complete: the remaining fields are dropped instead of making the coach wait for the whole
 * payload, and no spectator message is produced for the lines that carry no identifier.
 *
 * A read is otherwise considered finished when either:
 *  - the scanner's end-of-read suffix was seen and nothing followed for [terminatorGraceMs]
 *    (the suffix is Enter / CR / Tab), or
 *  - no character arrived for [idleTimeoutMs] (fallback for scanners that send no suffix).
 *
 * The short grace period that follows a suffix is what makes a multi-line payload safe:
 * most scanners transmit the line breaks of a multi-line QR code as Enter key events,
 * and those must not be mistaken for the end of the read — that used to split a single
 * card into one delivery per line/field.
 *
 * Guarantees:
 *  - characters are only accumulated while the read is in progress; no lookup is
 *    triggered per character;
 *  - the payload keeps its line structure, which the card parsers rely on;
 *  - the same physical read can never produce more than one callback;
 *  - an identical raw code retransmitted immediately by the scanner is ignored;
 *  - the buffer is always emptied after a read, so a stray character can never leak
 *    into the next scan;
 *  - any character arriving during a read cancels the pending end-of-read timer, so
 *    neither the suffix grace nor the idle fallback can fire while the scanner is
 *    still transmitting.
 *
 * The class only depends on the abstract [Scheduler] backend, which makes the whole
 * reception/timing logic unit testable on the JVM.
 */
class DouchetteReceiver(
    private val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
    private val terminatorGraceMs: Long = DEFAULT_TERMINATOR_GRACE_MS,
    private val duplicateWindowMs: Long = DEFAULT_DUPLICATE_WINDOW_MS,
    private val minCodeLength: Int = MIN_CODE_LENGTH,
    private val scheduler: Scheduler = HandlerScheduler(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val onCode: (String) -> Unit,
    private val onIncomplete: () -> Unit = {}
) {

    /** Abstraction over the delayed-execution backend so the logic can be unit tested. */
    interface Scheduler {
        fun schedule(runnable: Runnable, delayMs: Long)
        fun cancel(runnable: Runnable)
    }

    private class HandlerScheduler : Scheduler {
        private val handler = Handler(Looper.getMainLooper())
        override fun schedule(runnable: Runnable, delayMs: Long) {
            handler.postDelayed(runnable, delayMs)
        }
        override fun cancel(runnable: Runnable) {
            handler.removeCallbacks(runnable)
        }
    }

    private val buffer = StringBuilder()
    private val busy = AtomicBoolean(false)
    private var lastDeliveredCode: String = ""
    private var lastDeliveredAt: Long = 0L

    /**
     * True once the current read already produced its identifier: the remaining fields of a
     * multi-line card payload must be dropped, and they must never raise a second feedback.
     */
    private var burstDone = false

    private val endOfReadRunnable = Runnable { finish() }

    /** Appends one printable character received from the scanner. */
    fun feedChar(c: Char) {
        if (isTransport(c)) {
            // A line break inside the payload and the end-of-read suffix are the same
            // event on the wire: remember it but keep the door open for more characters.
            noteTerminator()
            return
        }
        append(c)
        scheduleEndOfRead(idleTimeoutMs)
        deliverEarlyIfIdentifierIsComplete()
    }

    /** Appends a whole string at once (ACTION_MULTIPLE / very fast bursts). */
    fun feedString(value: String) {
        if (value.isEmpty()) return
        var appended = false
        value.forEach { c ->
            if (isTransport(c)) {
                noteTerminator()
                return@forEach
            }
            if (append(c)) {
                appended = true
            } else {
                return
            }
        }
        if (appended) scheduleEndOfRead(idleTimeoutMs)
        deliverEarlyIfIdentifierIsComplete()
    }

    /** Signals the end-of-read suffix sent by the scanner (Enter / CR / Tab). */
    fun feedTerminator() {
        noteTerminator()
    }

    /** Aborts the current read, dropping any partially received characters. */
    fun reset() {
        scheduler.cancel(endOfReadRunnable)
        buffer.setLength(0)
        burstDone = false
    }

    /** Appends one character; returns false when the read is over and nothing else must be kept. */
    private fun append(c: Char): Boolean {
        if (burstDone) return false
        buffer.append(c)
        if (buffer.length > MAX_CODE_LENGTH) {
            // Safety net for a scanner that never sends a terminator: never let the
            // buffer grow without bound.
            finish()
            return false
        }
        return true
    }

    /**
     * A separator arrived. It ends the read only when nothing else follows during
     * [terminatorGraceMs], which coalesces the line breaks of a multi-line payload into
     * a single read while keeping a normal Enter-terminated scan instantaneous.
     */
    private fun noteTerminator() {
        if (buffer.isEmpty()) return
        // Keep the break: it is what tells the card parsers where one field ends. On the wire
        // it is indistinguishable from the scanner's end-of-read suffix, so the read is only
        // declared over after the grace period (or by the identifier below).
        if (buffer.last() != '\n') buffer.append('\n')
        scheduleEndOfRead(terminatorGraceMs)
        deliverEarlyIfIdentifierIsComplete()
    }

    /**
     * The identifier of a club card is on the second line of the QR payload ("N: 121" behind
     * "JUDO CLUB SEDDOUK"). As soon as that line is complete there is nothing left to wait for:
     * deliver now and let the rest of the payload fall on the floor.
     */
    private fun deliverEarlyIfIdentifierIsComplete() {
        if (burstDone) return
        val raw = buffer.toString()
        val lastBreak = raw.lastIndexOf('\n')
        if (lastBreak <= 0) return

        val code = extractCardId(raw.substring(0, lastBreak)) ?: return
        burstDone = true
        buffer.setLength(0)
        deliver(code)
        // Keep watching for the end of this read so the flag is cleared for the next scan.
        scheduleEndOfRead(idleTimeoutMs)
    }

    private fun scheduleEndOfRead(delayMs: Long) {
        scheduler.cancel(endOfReadRunnable)
        scheduler.schedule(endOfReadRunnable, delayMs)
    }

    private fun finish() {
        scheduler.cancel(endOfReadRunnable)

        if (burstDone) {
            // This read was already delivered on its identifier line: drop the trailing fields.
            burstDone = false
            buffer.setLength(0)
            return
        }

        val raw = buffer.toString()
        buffer.setLength(0)

        val code = sanitize(raw)

        if (code.isBlank()) {
            // A lone terminator (empty buffer) is not a scan at all: stay silent rather
            // than reporting a phantom "incomplete".
            return
        }
        if (code.length < minCodeLength) {
            onIncomplete()
            return
        }

        deliver(code)
    }

    private fun deliver(code: String) {
        val now = clock()
        if (code == lastDeliveredCode && (now - lastDeliveredAt) < duplicateWindowMs) {
            return
        }

        if (!busy.compareAndSet(false, true)) return
        lastDeliveredCode = code
        lastDeliveredAt = now
        try {
            onCode(code)
        } finally {
            busy.set(false)
        }
    }

    /** Line breaks and tabs are separators, never part of the code itself. */
    private fun isTransport(c: Char): Boolean = c == '\n' || c == '\r' || c == '\t'

    /**
     * Normalises the read. A structured card payload keeps its line breaks — the card parsers
     * read it field by field ("N: 121" on its own line) — while an ordinary code that a noisy
     * scanner happened to split is glued back together.
     */
    private fun sanitize(raw: String): String = normalizePayload(raw)

    companion object {
        private const val APP_CODE_PREFIX = "JCS-"

        /**
         * Fallback used when the scanner sends no suffix at all: the read is declared over
         * once the character stream goes quiet for this long. Every character resets it, so
         * it only needs to exceed the gap the scanner leaves between two characters
         * (an HID scanner emits a key every few tens of milliseconds), not the whole read.
         * Kept short so a suffix-less scanner does not make the coach wait after scanning.
         */
        const val DEFAULT_IDLE_TIMEOUT_MS: Long = 400L

        /**
         * Grace period granted after a suffix before the read is declared over, so the
         * line breaks of a multi-line payload are not mistaken for the end of the read.
         * A scanner transmits those interior line breaks back to back (single-digit
         * milliseconds apart), so the grace only has to bridge that burst while staying
         * imperceptible for a normal Enter-terminated scan.
         */
        const val DEFAULT_TERMINATOR_GRACE_MS: Long = 70L

        const val DEFAULT_DUPLICATE_WINDOW_MS: Long = 700L
        const val MIN_CODE_LENGTH: Int = 3

        /**
         * Generous upper bound: a multi-line sports-card payload is far longer than a
         * bare identifier, and truncating it mid-read would look like a partial scan.
         */
        private const val MAX_CODE_LENGTH: Int = 2048

        private const val MAX_IDENTIFIER_LENGTH: Int = 20

        /**
         * The number line of a club card: "N: 121", "N° 121", "No : 121", "No 121",
         * "Numéro de carte: 121". A separator (or the degree sign) is required, so a bare
         * number is never read as a labelled one.
         */
        private val CLUB_CARD_NUMBER_REGEX = Regex(
            """(?i)^(?:n\s*[°º]|n\s*[:=]|n\s*[o0]\s*[:=.]?|num(?:[eé]ro)?(?:\s*de)?(?:\s*(?:carte|licence|cin))?)\s*[:=.]?\s*([0-9]{1,8})$"""
        )

        /** An identifier this application itself produced ("JCS-121"). */
        private val APP_CODE_REGEX = Regex("""(?i)jcs[-\s]?[0-9]{1,8}""")

        /**
         * A line that looks like a field of the club's membership card ("N: 355", "Adherent:",
         * "Nom:", "Ne(e) le:", "Tel tuteur:", "Saison:"). Such a payload must keep its line
         * breaks, otherwise the card parser can no longer read the number back.
         */
        private val CLUB_CARD_FIELD_REGEX = Regex(
            """(?i)^(?:n\s*[°º]?\s*[:=]|adherent|adh[eé]rent|nom|pr[eé]nom|n[eé]\s*\(?e\)?\s*le|naissance|tel(?:[eé]phone)?|saison|club|categorie|cat[eé]gorie|ceinture|groupe|date)\b"""
        )

        /** A bare single-line barcode: "121", "JS-2026-0001", "ABC123". */
        private val BARE_CODE_REGEX = Regex("""[A-Za-z0-9][A-Za-z0-9-]{0,19}""")

        private val NAMES_THAT_ARE_LABELS = setOf("nom", "prénom", "prenom", "naissance", "saison")

        /**
         * Normalises a raw scanner payload the same way the reader does: a structured card
         * payload keeps its line breaks — the card parsers read it field by field ("N: 121"
         * on its own line) — while an ordinary code that a noisy scanner happened to split
         * is glued back together. Exposed so every scan path (HID douchette and camera)
         * feeds the app the same shape of payload.
         */
        fun normalizePayload(raw: String): String {
            val lines = raw.replace("\r\n", "\n").replace('\r', '\n')
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (lines.isEmpty()) return ""
            val isStructuredCardPayload = lines.any {
                CLUB_CARD_NUMBER_REGEX.matches(it) || CLUB_CARD_FIELD_REGEX.containsMatchIn(it)
            }
            return if (isStructuredCardPayload) lines.joinToString("\n") else lines.joinToString("")
        }

        /**
         * Pulls the identifier out of the raw scanner payload, or null while the payload still
         * looks incomplete. Only COMPLETE lines must be passed here: the caller slices the
         * buffer at its last line break, so a half-received "N: 1" can never be mistaken for
         * the card number "1".
         */
        internal fun extractCardId(payload: String): String? {
            val lines = payload.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return null

            for (line in lines) {
                val match = CLUB_CARD_NUMBER_REGEX.find(line) ?: continue
                val digits = match.groupValues[1]
                if (digits.isNotEmpty()) return APP_CODE_PREFIX + digits
            }

            for (line in lines) {
                if (line.length <= MAX_IDENTIFIER_LENGTH && APP_CODE_REGEX.matches(line)) {
                    return line.uppercase(java.util.Locale.ROOT)
                }
            }

            // A bare identifier is only trusted when the whole read is that single code: a number
            // printed inside a multi-line card payload is never the identifier of the card.
            if (lines.size == 1) {
                val only = lines.first()
                if (only.length <= MAX_IDENTIFIER_LENGTH &&
                    only.any { it.isDigit() } &&
                    BARE_CODE_REGEX.matches(only) &&
                    only.lowercase(java.util.Locale.ROOT) !in NAMES_THAT_ARE_LABELS
                ) {
                    return only
                }
            }
            return null
        }
    }
}
