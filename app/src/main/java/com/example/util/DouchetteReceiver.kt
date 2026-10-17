package com.example.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Receives the character stream produced by a USB / Bluetooth HID barcode scanner
 * ("douchette") and delivers exactly ONE complete code per physical read.
 *
 * A read is considered finished when either:
 *  - a terminator is received (the suffix the scanner is configured to append, e.g.
 *    Enter / CR), or
 *  - no character arrived for [idleTimeoutMs] (fallback for scanners that send no suffix).
 *
 * Guarantees:
 *  - characters are only accumulated while the read is in progress; no lookup is
 *    triggered per character;
 *  - the same physical read can never produce more than one callback;
 *  - an identical raw code retransmitted immediately by the scanner is ignored;
 *  - the buffer is always emptied after a read, so a stray character can never leak
 *    into the next scan;
 *  - any character arriving during a read resets the idle timer, so the fallback can
 *    never fire while the scanner is still transmitting.
 *
 * The class only depends on the abstract [Scheduler] backend, which makes the whole
 * reception/timing logic unit testable on the JVM.
 */
class DouchetteReceiver(
    private val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
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

    private val idleRunnable = Runnable { finish(fromTerminator = false) }

    /** Appends one printable character received from the scanner. */
    fun feedChar(c: Char) {
        buffer.append(c)
        if (buffer.length > MAX_CODE_LENGTH) {
            // Safety net for a scanner that never sends a terminator: never let the
            // buffer grow without bound.
            finish(fromTerminator = false)
            return
        }
        restartIdleTimer()
    }

    /** Appends a whole string at once (ACTION_MULTIPLE / very fast bursts). */
    fun feedString(value: String) {
        if (value.isEmpty()) return
        value.forEach { c ->
            buffer.append(c)
            if (buffer.length > MAX_CODE_LENGTH) {
                finish(fromTerminator = false)
                return
            }
        }
        restartIdleTimer()
    }

    /** Signals the end-of-read suffix sent by the scanner (Enter / CR / Tab). */
    fun feedTerminator() {
        finish(fromTerminator = true)
    }

    /** Aborts the current read, dropping any partially received characters. */
    fun reset() {
        scheduler.cancel(idleRunnable)
        buffer.setLength(0)
    }

    private fun restartIdleTimer() {
        scheduler.cancel(idleRunnable)
        scheduler.schedule(idleRunnable, idleTimeoutMs)
    }

    private fun finish(fromTerminator: Boolean) {
        scheduler.cancel(idleRunnable)

        val raw = buffer.toString()
        buffer.setLength(0)

        val code = sanitize(raw)

        if (code.isBlank()) {
            // A lone terminator, or a read containing only transport characters, is not
            // a scan at all: stay silent rather than reporting a phantom "incomplete".
            if (fromTerminator && raw.isNotEmpty()) onIncomplete()
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

    private fun sanitize(raw: String): String =
        raw.replace("\n", "").replace("\r", "").replace("\t", "").trim()

    companion object {
        /**
         * Preferred configuration: set the scanner to append an Enter (CR) suffix. This
         * timeout is only the fallback used when the scanner sends no suffix at all.
         */
        const val DEFAULT_IDLE_TIMEOUT_MS: Long = 300L
        const val DEFAULT_DUPLICATE_WINDOW_MS: Long = 700L
        const val MIN_CODE_LENGTH: Int = 3
        private const val MAX_CODE_LENGTH: Int = 256
    }
}
