package com.example

import com.example.file.formatLiteralInt
import com.example.file.formatLiteralLong
import com.example.file.isDefaultValue
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testFormatLiteralInt() {
    assertEquals("0x0", formatLiteralInt(0))
    assertEquals("0x1", formatLiteralInt(1))
    assertEquals("0x10", formatLiteralInt(16))
    assertEquals("0x7fffffff", formatLiteralInt(Int.MAX_VALUE))
    assertEquals("-0x1", formatLiteralInt(-1))
    assertEquals("-0x8", formatLiteralInt(-8))
    assertEquals("-0x80000000", formatLiteralInt(Int.MIN_VALUE))
  }

  @Test
  fun testFormatLiteralLong() {
    assertEquals("0x0L", formatLiteralLong(0L))
    assertEquals("0x1L", formatLiteralLong(1L))
    assertEquals("0x10L", formatLiteralLong(16L))
    assertEquals("0x7fffffffffffffffL", formatLiteralLong(Long.MAX_VALUE))
    assertEquals("-0x1L", formatLiteralLong(-1L))
    assertEquals("-0x8000000000000000L", formatLiteralLong(Long.MIN_VALUE))
  }

  @Test
  fun testIsDefaultValue() {
    assertTrue(isDefaultValue("int", "0"))
    assertTrue(isDefaultValue("int", "0x0"))
    assertTrue(isDefaultValue("long", "0x0L"))
    assertTrue(isDefaultValue("boolean", "0x0"))
    assertFalse(isDefaultValue("int", "0x1"))
  }
}

