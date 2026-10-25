package com.example.parser

import com.example.data.model.ScreenshotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class TaskParserTest {

    private val parser = TaskParser(defaultReminderHour = 9, defaultReminderMinute = 0)
    // Fixed base date: Wednesday, Oct 21, 2026 at 10:00 AM
    private val baseDateTime = LocalDateTime.of(2026, 10, 21, 10, 0)

    @Test
    fun testCallMomTomorrow6pm() {
        val input = "Call mom tomorrow 6pm"
        val tasks = parser.parseTasks(input, baseDateTime)

        assertEquals(1, tasks.size)
        val task = tasks[0]
        assertTrue(task.title.contains("Call mom", ignoreCase = true))

        assertNotNull(task.dueAt)
        val dueInstant = java.time.Instant.ofEpochMilli(task.dueAt!!)
        val dueLocal = LocalDateTime.ofInstant(dueInstant, ZoneId.systemDefault())

        // Tomorrow is Oct 22, 2026 at 18:00
        assertEquals(2026, dueLocal.year)
        assertEquals(10, dueLocal.monthValue)
        assertEquals(22, dueLocal.dayOfMonth)
        assertEquals(18, dueLocal.hour)
        assertEquals(0, dueLocal.minute)
    }

    @Test
    fun testBulletListWithDates() {
        val input = "- buy milk\n- pay electricity bill by 15th\n- book dentist"
        val tasks = parser.parseTasks(input, baseDateTime)

        assertEquals(3, tasks.size)
        assertTrue(tasks[0].title.contains("buy milk", ignoreCase = true))
        assertTrue(tasks[1].title.contains("pay electricity bill", ignoreCase = true))
        assertTrue(tasks[2].title.contains("book dentist", ignoreCase = true))

        // Second task has due date on the 15th (next month since 21st is past 15th)
        assertNotNull(tasks[1].dueAt)
        val dueInstant = java.time.Instant.ofEpochMilli(tasks[1].dueAt!!)
        val dueLocal = LocalDateTime.ofInstant(dueInstant, ZoneId.systemDefault())
        assertEquals(15, dueLocal.dayOfMonth)
    }

    @Test
    fun testChatBubbleSendReportByFriday() {
        val input = "Can you send the report by Friday?"
        val tasks = parser.parseTasks(input, baseDateTime)

        assertEquals(1, tasks.size)
        val task = tasks[0]
        assertTrue(task.title.contains("Send the report", ignoreCase = true))

        assertNotNull(task.dueAt)
        val dueInstant = java.time.Instant.ofEpochMilli(task.dueAt!!)
        val dueLocal = LocalDateTime.ofInstant(dueInstant, ZoneId.systemDefault())

        // Oct 21, 2026 is Wednesday. Friday is Oct 23, 2026
        assertEquals(DayOfWeek.FRIDAY, dueLocal.dayOfWeek)
        assertEquals(23, dueLocal.dayOfMonth)
        assertEquals(9, dueLocal.hour) // default reminder hour
    }

    @Test
    fun testSensitiveContentFilter() {
        // OTP test
        assertTrue(parser.isSensitive("Your Google verification code is 482910. Do not share."))
        assertTrue(parser.isSensitive("Use OTP: 98124 to log in"))

        // Password test
        assertTrue(parser.isSensitive("Account password: MySecretPassword123!"))
        assertTrue(parser.isSensitive("Enter your passcode"))

        // Credit card Luhn test
        // 4532 0150 0000 0003 is a valid test Visa card number by Luhn
        assertTrue(parser.isSensitive("Card payment 4532 0150 0000 0003 CVV 819"))

        // Non-sensitive regular message
        assertFalse(parser.isSensitive("Hey, let's meet at 2pm tomorrow for lunch at Joe's Pizza!"))
        assertFalse(parser.isSensitive("- buy groceries\n- call dentist"))
    }

    @Test
    fun testClassification() {
        assertEquals(ScreenshotType.LIST, parser.classify("- Item 1\n- Item 2\n- Item 3"))
        assertEquals(ScreenshotType.CHAT, parser.classify("Can you send me the files? Today at 3:15 PM"))
        assertEquals(ScreenshotType.EVENT, parser.classify("Annual Developer Conference\nStarts at 10 AM on Zoom"))
    }
}
