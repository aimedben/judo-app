package com.example.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Receives the character stream produced by a USB / Bluetooth HID barcode scanner
 * ("douchette") and delivers exactly ONE complete code per physical read.
 *
 * A read is considered finished when either:
 *  - the scanner's end-of-read suffix was seen and nothing followed for
 *    [terminatorGraceMs] (the suffix is Enter / CR / Tab), or
 *  - no character arrived for [idleTimeoutMs] (fallback for scanners that send no suffix).
 *
 * The short grace period that follows a suffix is what makes a multi-line payload safe:
 * most scanners transmit the line breaks of a multi-line QR code as Enter key events,
 * and those must not be mistaken for the end of the read — that used to split a single
 * card into one delivery per line/field. Because the grace also protects a slow scanner
 * (characters spaced further apart than a naive timeout), a whole read always ends up in
 * a single delivery.
 *
 * Guarantees:
 *  - characters are only accumulated while the read is in progress; no lookup is
 *    triggered per character;
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

    private val endOfReadRunnable = Runnable { finish() }

    /** Appends one printable character received from the scanner. */
    fun feedChar(c: Char) {
        if (isTransport(c)) {
            // A line break inside the payload and the end-of-read suffix are the same
            // event on the wire: remember it but keep the door open for more characters.
            noteTerminator()
            return
        }
        buffer.append(c)
        if (buffer.length > MAX_CODE_LENGTH) {
            // Safety net for a scanner that never sends a terminator: never let the
            // buffer grow without bound.
            finish()
            return
        }
        scheduleEndOfRead(idleTimeoutMs)
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
            buffer.append(c)
            appended = true
            if (buffer.length > MAX_CODE_LENGTH) {
                finish()
                return
            }
        }
        if (appended) scheduleEndOfRead(idleTimeoutMs)
    }

    /** Signals the end-of-read suffix sent by the scanner (Enter / CR / Tab). */
    fun feedTerminator() {
        noteTerminator()
    }

    /** Aborts the current read, dropping any partially received characters. */
    fun reset() {
        scheduler.cancel(endOfReadRunnable)
        buffer.setLength(0)
    }

    /**
     * A separator arrived. It ends the read only when nothing else follows during
     * [terminatorGraceMs], which coalesces the line breaks of a multi-line payload into
     * a single read while keeping a normal Enter-terminated scan instantaneous.
     */
    private fun noteTerminator() {
        if (buffer.isEmpty()) return
        scheduleEndOfRead(terminatorGraceMs)
    }

    private fun scheduleEndOfRead(delayMs: Long) {
        scheduler.cancel(endOfReadRunnable)
        scheduler.schedule(endOfReadRunnable, delayMs)
    }

    private fun finish() {
        scheduler.cancel(endOfReadRunnable)

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

    private fun sanitize(raw: String): String =
        raw.replace("\n", "").replace("\r", "").replace("\t", "").trim()

    companion object {
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
    }
}
