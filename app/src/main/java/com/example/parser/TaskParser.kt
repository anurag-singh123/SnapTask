package com.example.parser

import com.example.data.model.ParsedTaskCandidate
import com.example.data.model.ScreenshotType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.regex.Pattern

class TaskParser(
    private val defaultReminderHour: Int = 9,
    private val defaultReminderMinute: Int = 0
) {

    companion object {
        private val ACTION_VERBS = setOf(
            "call", "email", "buy", "pay", "book", "submit", "send", "renew",
            "remind", "pick up", "follow up", "check", "order", "finish", "review",
            "return", "cancel", "clean", "fix", "schedule", "prepare", "read",
            "ask", "message", "text", "update", "attend", "deposit", "transfer",
            "meet", "complete", "sign", "print", "reply"
        )

        private val BULLET_PREFIX_REGEX = Regex("^(?:[-*•–—]|\\(?\\d+[.)\\]]|(?:\\[\\s*[\\]xX]?\\s*))\\s*")

        private val URL_REGEX = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+|www\\.[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
        private val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        private val PHONE_REGEX = Regex("(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}")

        private val OTP_REGEX = Regex("(?i)\\b(?:otp|verification\\s*code|security\\s*code|login\\s*code|auth\\s*code)\\b|\\bcode\\s*[:=]\\s*\\d{4,8}\\b|\\b(?:use|is your)\\s+code\\s+\\d{4,8}\\b")
        private val CVV_REGEX = Regex("(?i)\\b(?:cvv|cvc|cid)\\s*[:=]?\\s*\\d{3,4}\\b")
        private val PASSWORD_REGEX = Regex("(?i)\\b(?:password|passcode|secret\\s*pin)\\b")
        private val CARD_CANDIDATE_REGEX = Regex("\\b(?:\\d[ -]*?){13,19}\\b")
    }

    /**
     * Checks if OCR text contains sensitive information like OTPs, credit cards, or passwords.
     */
    fun isSensitive(text: String): Boolean {
        if (text.isBlank()) return false

        // Password / passcode
        if (PASSWORD_REGEX.containsMatchIn(text)) return true

        // OTP / verification code
        if (OTP_REGEX.containsMatchIn(text)) return true

        // CVV / CVC
        if (CVV_REGEX.containsMatchIn(text)) return true

        // Credit Card Number with Luhn Check
        val matcher = CARD_CANDIDATE_REGEX.toPattern().matcher(text)
        while (matcher.find()) {
            val digitsOnly = matcher.group().replace(Regex("[\\s-]"), "")
            if (digitsOnly.length in 13..19 && isValidLuhn(digitsOnly)) {
                return true
            }
        }

        return false
    }

    private fun isValidLuhn(digits: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i].digitToIntOrNull() ?: return false
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }

    /**
     * Classifies screenshot text into CHAT, LIST, EVENT, or OTHER.
     */
    fun classify(text: String): ScreenshotType {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val bulletCount = lines.count { BULLET_PREFIX_REGEX.containsMatchIn(it) }
        if (bulletCount >= 2) return ScreenshotType.LIST

        val lower = text.lowercase(Locale.ROOT)
        if (lower.contains("meeting") || lower.contains("webinar") || lower.contains("starts at") ||
            lower.contains("google meet") || lower.contains("zoom") || lower.contains("calendar invite")
        ) {
            return ScreenshotType.EVENT
        }

        if (lower.contains("can you") || lower.contains("could you") || lower.contains("please send") ||
            lower.contains("yesterday at") || lower.contains("today at") || lower.contains("am:") ||
            lower.contains("pm:") || lower.contains("typing...") || lower.contains("online")
        ) {
            return ScreenshotType.CHAT
        }

        return ScreenshotType.OTHER
    }

    /**
     * Parses candidate tasks from OCR text.
     */
    fun parseTasks(
        ocrText: String,
        baseDateTime: LocalDateTime = LocalDateTime.now()
    ): List<ParsedTaskCandidate> {
        val cleanText = ocrText.trim()
        if (cleanText.isBlank()) return emptyList()

        val type = classify(cleanText)
        val rawLines = cleanText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val mergedLines = mergeWrappedLines(rawLines)

        val candidates = mutableListOf<ParsedTaskCandidate>()

        for (line in mergedLines) {
            val candidate = parseLineAsCandidate(line, baseDateTime, type)
            if (candidate != null) {
                candidates.add(candidate)
            }
        }

        // If no candidate was extracted, provide the fallback task
        if (candidates.isEmpty()) {
            val firstLine = rawLines.firstOrNull()?.take(60) ?: "Screenshot"
            val fallbackTitle = if (firstLine.length < 50) "Review: $firstLine" else "Review screenshot"
            val links = extractLinks(cleanText)
            candidates.add(
                ParsedTaskCandidate(
                    title = fallbackTitle,
                    notes = cleanText.take(200),
                    dueAt = null,
                    remindAt = null,
                    extractedLinks = links,
                    isSelected = true
                )
            )
        }

        return candidates
    }

    private fun mergeWrappedLines(lines: List<String>): List<String> {
        val merged = mutableListOf<String>()
        var current = ""

        for (line in lines) {
            val isBullet = BULLET_PREFIX_REGEX.containsMatchIn(line)
            val startsWithCapitalOrAction = line.firstOrNull()?.isUpperCase() == true || startsWithActionVerb(line)

            if (current.isEmpty()) {
                current = line
            } else if (isBullet || startsWithCapitalOrAction || current.endsWith(".") || current.endsWith("?") || current.endsWith("!")) {
                merged.add(current)
                current = line
            } else {
                current = "$current $line"
            }
        }
        if (current.isNotEmpty()) {
            merged.add(current)
        }
        return merged
    }

    private fun parseLineAsCandidate(
        line: String,
        baseDateTime: LocalDateTime,
        type: ScreenshotType
    ): ParsedTaskCandidate? {
        val isBullet = BULLET_PREFIX_REGEX.containsMatchIn(line)
        val stripped = line.replace(BULLET_PREFIX_REGEX, "").trim()
        if (stripped.length < 3) return null

        val (dateTime, cleanedTitle) = extractDateTime(stripped, baseDateTime)
        val hasAction = containsActionVerb(cleanedTitle)
        val isChatRequest = isChatRequestSentence(cleanedTitle)

        val shouldInclude = when (type) {
            ScreenshotType.LIST -> isBullet || hasAction
            ScreenshotType.CHAT -> isChatRequest || hasAction || dateTime != null
            ScreenshotType.EVENT -> dateTime != null || hasAction
            ScreenshotType.OTHER -> isBullet || hasAction || (dateTime != null && stripped.length <= 100)
        }

        if (!shouldInclude) return null

        // Clean title
        var finalTitle = cleanTaskTitle(cleanedTitle)
        if (finalTitle.length < 3) return null

        // Extract links / contact info
        val links = extractLinks(line)

        val dueMillis = dateTime?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        val remindMillis = dueMillis // Remind at due time by default

        return ParsedTaskCandidate(
            title = finalTitle,
            notes = if (links.isNotEmpty()) "Extracted details: ${links.joinToString(", ")}" else "",
            dueAt = dueMillis,
            remindAt = remindMillis,
            extractedLinks = links,
            isSelected = true
        )
    }

    private fun startsWithActionVerb(text: String): Boolean {
        val firstWord = text.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).firstOrNull() ?: return false
        return ACTION_VERBS.contains(firstWord)
    }

    private fun containsActionVerb(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        for (verb in ACTION_VERBS) {
            val pattern = Regex("\\b${Pattern.quote(verb)}\\b")
            if (pattern.containsMatchIn(lower)) return true
        }
        return false
    }

    private fun isChatRequestSentence(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.startsWith("can you") || lower.startsWith("could you") ||
                lower.startsWith("please") || lower.contains("need you to") ||
                lower.contains("don't forget to") || lower.contains("remember to")
    }

    private fun cleanTaskTitle(text: String): String {
        var clean = text.trim()
        // Strip question starters in chat context
        val prefixesToStrip = listOf(
            Regex("(?i)^can you please\\s+"),
            Regex("(?i)^can you\\s+"),
            Regex("(?i)^could you please\\s+"),
            Regex("(?i)^could you\\s+"),
            Regex("(?i)^please\\s+"),
            Regex("(?i)^remember to\\s+"),
            Regex("(?i)^don't forget to\\s+")
        )
        for (prefix in prefixesToStrip) {
            clean = clean.replace(prefix, "")
        }
        // Capitalize first character
        if (clean.isNotEmpty()) {
            clean = clean.substring(0, 1).uppercase(Locale.ROOT) + clean.substring(1)
        }
        return clean.removeSuffix("?").removeSuffix(".").trim()
    }

    /**
     * Extracts date and time from a text snippet, removing the date tokens from title.
     */
    fun extractDateTime(
        text: String,
        baseDateTime: LocalDateTime
    ): Pair<LocalDateTime?, String> {
        var resultDate: LocalDate? = null
        var resultTime: LocalTime? = null
        var workingText = text

        // 1. "in X hours" or "in X mins/minutes"
        val inHoursMatch = Regex("(?i)\\bin\\s+(\\d+)\\s*(?:hours?|hrs?)\\b").find(workingText)
        if (inHoursMatch != null) {
            val hours = inHoursMatch.groupValues[1].toLongOrNull() ?: 1
            val dt = baseDateTime.plusHours(hours)
            workingText = workingText.removeRange(inHoursMatch.range).trim()
            return Pair(dt, cleanUpPunctuation(workingText))
        }

        val inMinsMatch = Regex("(?i)\\bin\\s+(\\d+)\\s*(?:mins?|minutes?)\\b").find(workingText)
        if (inMinsMatch != null) {
            val mins = inMinsMatch.groupValues[1].toLongOrNull() ?: 15
            val dt = baseDateTime.plusMinutes(mins)
            workingText = workingText.removeRange(inMinsMatch.range).trim()
            return Pair(dt, cleanUpPunctuation(workingText))
        }

        // 2. "tomorrow"
        val tomorrowMatch = Regex("(?i)\\btomorrow\\b").find(workingText)
        if (tomorrowMatch != null) {
            resultDate = baseDateTime.toLocalDate().plusDays(1)
            workingText = workingText.removeRange(tomorrowMatch.range).trim()
        }

        // 3. "today"
        if (resultDate == null) {
            val todayMatch = Regex("(?i)\\btoday\\b").find(workingText)
            if (todayMatch != null) {
                resultDate = baseDateTime.toLocalDate()
                workingText = workingText.removeRange(todayMatch.range).trim()
            }
        }

        // 4. "by EOD" / "EOD"
        val eodMatch = Regex("(?i)\\b(?:by\\s+)?eod\\b").find(workingText)
        if (eodMatch != null) {
            if (resultDate == null) resultDate = baseDateTime.toLocalDate()
            resultTime = LocalTime.of(18, 0)
            workingText = workingText.removeRange(eodMatch.range).trim()
        }

        // 5. Day of week: "Fri", "Friday", "next Monday"
        val daysOfWeek = mapOf(
            "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
            "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
            "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
            "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY
        )

        val dayPattern = Regex("(?i)\\b(?:by\\s+|on\\s+)?(?:(next)\\s+)?(monday|mon|tuesday|tue|tues|wednesday|wed|thursday|thu|thur|thurs|friday|fri|saturday|sat|sunday|sun)\\b")
        val dayMatch = dayPattern.find(workingText)
        if (dayMatch != null && resultDate == null) {
            val isNext = dayMatch.groupValues[1].isNotEmpty()
            val dayName = dayMatch.groupValues[2].lowercase(Locale.ROOT)
            val targetDay = daysOfWeek[dayName]
            if (targetDay != null) {
                var candidateDate = baseDateTime.toLocalDate().with(TemporalAdjusters.next(targetDay))
                if (isNext && candidateDate.isEqual(baseDateTime.toLocalDate().with(TemporalAdjusters.next(targetDay)))) {
                    // Next week's instance
                    candidateDate = candidateDate.plusWeeks(1)
                }
                resultDate = candidateDate
                workingText = workingText.removeRange(dayMatch.range).trim()
            }
        }

        // 6. "by 15th" or "on the 15th"
        val ordinalMatch = Regex("(?i)\\b(?:by|on|before)\\s+(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\b").find(workingText)
        if (ordinalMatch != null && resultDate == null) {
            val dayNum = ordinalMatch.groupValues[1].toIntOrNull()
            if (dayNum != null && dayNum in 1..31) {
                val currentMonth = baseDateTime.toLocalDate()
                val target = try {
                    if (dayNum >= currentMonth.dayOfMonth) {
                        currentMonth.withDayOfMonth(dayNum)
                    } else {
                        currentMonth.plusMonths(1).withDayOfMonth(dayNum)
                    }
                } catch (e: Exception) {
                    null
                }
                if (target != null) {
                    resultDate = target
                    workingText = workingText.removeRange(ordinalMatch.range).trim()
                }
            }
        }

        // 7. Explicit date: "12/11", "11 Nov", "Nov 11"
        val slashDateMatch = Regex("(?i)\\b(\\d{1,2})[/.-](\\d{1,2})(?:[/.-](\\d{2,4}))?\\b").find(workingText)
        if (slashDateMatch != null && resultDate == null) {
            val p1 = slashDateMatch.groupValues[1].toIntOrNull() ?: 1
            val p2 = slashDateMatch.groupValues[2].toIntOrNull() ?: 1
            val year = slashDateMatch.groupValues[3].toIntOrNull() ?: baseDateTime.year
            val normalizedYear = if (year < 100) 2000 + year else year
            // Determine day and month
            val date = try {
                if (p1 > 12) LocalDate.of(normalizedYear, p2, p1) else LocalDate.of(normalizedYear, p1, p2)
            } catch (e: Exception) {
                null
            }
            if (date != null) {
                resultDate = date
                workingText = workingText.removeRange(slashDateMatch.range).trim()
            }
        }

        val monthNameMatch = Regex("(?i)\\b(?:(\\d{1,2})\\s+)?(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec|january|february|march|april|june|july|august|september|october|november|december)(?:\\s+(\\d{1,2}))?(?:st|nd|rd|th)?\\b").find(workingText)
        if (monthNameMatch != null && resultDate == null) {
            val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
            val monthStr = monthNameMatch.groupValues[2].lowercase(Locale.ROOT).take(3)
            val monthIdx = months.indexOf(monthStr) + 1
            val d1 = monthNameMatch.groupValues[1].toIntOrNull()
            val d2 = monthNameMatch.groupValues[3].toIntOrNull()
            val day = d1 ?: d2 ?: 1
            if (monthIdx in 1..12 && day in 1..31) {
                val date = try {
                    LocalDate.of(baseDateTime.year, monthIdx, day)
                } catch (e: Exception) {
                    null
                }
                if (date != null) {
                    resultDate = if (date.isBefore(baseDateTime.toLocalDate())) date.plusYears(1) else date
                    workingText = workingText.removeRange(monthNameMatch.range).trim()
                }
            }
        }

        // 8. Time extraction: "6pm", "6:30 pm", "at 6pm", "18:00", "3 PM"
        val timePattern = Regex("(?i)(?:at\\s+)?\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b|\\b(\\d{1,2}):(\\d{2})\\b")
        val timeMatch = timePattern.find(workingText)
        if (timeMatch != null) {
            val hourStr = timeMatch.groupValues[1].ifEmpty { timeMatch.groupValues[4] }
            val minStr = timeMatch.groupValues[2].ifEmpty { timeMatch.groupValues[5] }
            val ampm = timeMatch.groupValues[3].lowercase(Locale.ROOT)

            var hour = hourStr.toIntOrNull() ?: 12
            val min = minStr.toIntOrNull() ?: 0

            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0

            if (hour in 0..23 && min in 0..59) {
                resultTime = LocalTime.of(hour, min)
                workingText = workingText.removeRange(timeMatch.range).trim()
            }
        }

        // If time was specified but no date, assume today (or tomorrow if time already passed)
        if (resultTime != null && resultDate == null) {
            val candidate = baseDateTime.toLocalDate()
            resultDate = if (resultTime.isBefore(baseDateTime.toLocalTime())) candidate.plusDays(1) else candidate
        }

        // If date was specified but no time, use default reminder time
        if (resultDate != null && resultTime == null) {
            resultTime = LocalTime.of(defaultReminderHour, defaultReminderMinute)
        }

        val finalDateTime = if (resultDate != null && resultTime != null) {
            LocalDateTime.of(resultDate, resultTime)
        } else null

        // Clean up "at", "by", "on" left dangling at end or before stripped parts
        workingText = cleanUpPunctuation(workingText)

        return Pair(finalDateTime, workingText)
    }

    private fun cleanUpPunctuation(text: String): String {
        return text
            .replace(Regex("(?i)\\b(?:by|at|on|for)\\s*$"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private fun extractLinks(text: String): List<String> {
        val links = mutableListOf<String>()
        URL_REGEX.findAll(text).forEach { links.add(it.value) }
        EMAIL_REGEX.findAll(text).forEach { links.add(it.value) }
        PHONE_REGEX.findAll(text).forEach {
            val phone = it.value.trim()
            if (phone.length >= 7) links.add(phone)
        }
        return links.distinct()
    }
}
