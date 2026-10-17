package com.example

import com.example.util.SimSmsSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The destination field of the direct-SIM sender is a free-form coach input: pure parsing must
 * produce only usable club numbers, without touching the messaging of the absent notifier.
 */
class SimSmsSenderParseTest {

  @Test
  fun commaSeparatedNumbersAreNormalizedAndDeduplicated() {
    val result = SimSmsSender.parseDestinations("0783033616, 0794335010")
    assertEquals(listOf("0783033616", "0794335010"), result)
  }

  @Test
  fun clubDataFormatsAreAccepted() {
    val result = SimSmsSender.parseDestinations("07-80-25-46-56\n00213783033616;+213794335010")
    // All normalized into the national form, distinct.
    assertTrue(result.all { it.startsWith("0") && it.length in 9..10 })
    assertEquals(
      setOf("0780254656", "0783033616", "0794335010"),
      result.toSet()
    )
  }

  @Test
  fun junkTokensAndEmptyTokensAreIgnored() {
    val result = SimSmsSender.parseDestinations("test,, abcdef, 0783033616")
    assertEquals(listOf("0783033616"), result)
  }

  @Test
  fun emptyInputYieldsNoDestination() {
    assertTrue(SimSmsSender.parseDestinations("").isEmpty())
  }
}
