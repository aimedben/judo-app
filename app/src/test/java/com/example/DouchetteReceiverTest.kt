package com.example

import com.example.util.DouchetteReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DouchetteReceiverTest {

  private class FakeScheduler : DouchetteReceiver.Scheduler {
    var pending: Runnable? = null
    var lastDelay: Long = -1L
    var scheduleCount = 0

    override fun schedule(runnable: Runnable, delayMs: Long) {
      pending = runnable
      lastDelay = delayMs
      scheduleCount++
    }

    override fun cancel(runnable: Runnable) {
      if (pending === runnable) pending = null
    }

    fun fire() {
      val r = pending
      pending = null
      r?.run()
    }
  }

  private class Harness {
    val scheduler = FakeScheduler()
    var now = 1_000L
    val delivered = mutableListOf<String>()
    var incompleteCount = 0

    val receiver = DouchetteReceiver(
      idleTimeoutMs = 300L,
      duplicateWindowMs = 700L,
      minCodeLength = 3,
      scheduler = scheduler,
      clock = { now },
      onCode = { delivered += it },
      onIncomplete = { incompleteCount++ }
    )

    fun feed(code: String) = code.forEach { receiver.feedChar(it) }
    fun advance(ms: Long) { now += ms }
  }

  @Test
  fun longCodeIsNotProcessedPerCharacter() {
    val h = Harness()
    val code = "JCS-" + "9".repeat(120) // 124 characters, well past the old 50-char limit

    // Simulate the scanner streaming the characters one by one.
    for (c in code) {
      h.receiver.feedChar(c)
      // No lookup must ever happen while the characters are still arriving.
      assertTrue("Delivered prematurely after ${h.delivered.size}", h.delivered.isEmpty())
    }

    // The scanner sends the end-of-read suffix.
    h.receiver.feedTerminator()

    assertEquals("A complete read must yield exactly one result", 1, h.delivered.size)
    assertEquals("The full code must be preserved", code, h.delivered.first())
  }

  @Test
  fun terminatorDeliversExactlyOnceEvenIfSentTwice() {
    val h = Harness()
    h.feed("JCS-12345")
    h.receiver.feedTerminator()
    h.receiver.feedTerminator() // duplicate suffix, same instant
    assertEquals(1, h.delivered.size)
    assertEquals("JCS-12345", h.delivered.first())
  }

  @Test
  fun idleTimeoutDeliversCompleteCodeOnce() {
    val h = Harness()
    h.feed("JCS-4242")
    assertEquals(0, h.delivered.size) // nothing yet: still waiting
    h.scheduler.fire()                // inactivity timeout elapses
    assertEquals(1, h.delivered.size)
    assertEquals("JCS-4242", h.delivered.first())
  }

  @Test
  fun eachCharacterResetsTheIdleTimer() {
    val h = Harness()
    "JCS-1".forEach { h.receiver.feedChar(it) }
    // One schedule per character, plus the cancel before each reschedule.
    assertEquals(5, h.scheduler.scheduleCount)
    assertEquals(300L, h.scheduler.lastDelay)
    // A stale timeout must not have been left pending.
    assertTrue(h.delivered.isEmpty())
  }

  @Test
  fun transportCharactersAreStrippedButSignificantOnesKept() {
    val h = Harness()
    h.feed("JCS-\n6474\r\t")
    h.receiver.feedTerminator()
    assertEquals("JCS-6474", h.delivered.first())
  }

  @Test
  fun immediateDuplicateOfSameRawCodeIsIgnored() {
    val h = Harness()
    h.feed("JCS-1")
    h.receiver.feedTerminator()
    assertEquals(1, h.delivered.size)

    // The douchette retransmits the exact same code right away.
    h.feed("JCS-1")
    h.receiver.feedTerminator()
    assertEquals("An immediate identical retransmission must not double-fire", 1, h.delivered.size)
  }

  @Test
  fun sameCodeRescansAfterDuplicateWindow() {
    val h = Harness()
    h.feed("JCS-1"); h.receiver.feedTerminator()
    h.advance(1_000L) // beyond the duplicate window
    h.feed("JCS-1"); h.receiver.feedTerminator()
    assertEquals("A deliberate rescan later must still be delivered", 2, h.delivered.size)
  }

  @Test
  fun differentCodesAreDeliveredIndependently() {
    val h = Harness()
    h.feed("JCS-1"); h.receiver.feedTerminator()
    h.feed("JCS-2"); h.receiver.feedTerminator()
    h.feed("JCS-3"); h.receiver.feedTerminator()
    assertEquals(listOf("JCS-1", "JCS-2", "JCS-3"), h.delivered)
  }

  @Test
  fun loneTerminatorStaysSilent() {
    val h = Harness()
    h.receiver.feedTerminator()
    assertTrue(h.delivered.isEmpty())
    assertEquals(0, h.incompleteCount)
  }

  @Test
  fun tooShortReadIsReportedIncompleteOnce() {
    val h = Harness()
    h.feed("AB")
    h.receiver.feedTerminator()
    assertTrue(h.delivered.isEmpty())
    assertEquals(1, h.incompleteCount)
  }

  @Test
  fun resetDropsPartialCharactersFromPreviousScan() {
    val h = Harness()
    h.feed("JCS-1")          // interrupted read
    h.receiver.reset()       // e.g. session ended / aborted
    h.feed("JCS-2")
    h.receiver.feedTerminator()
    assertEquals(listOf("JCS-2"), h.delivered)
  }
}
