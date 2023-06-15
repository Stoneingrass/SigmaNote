
package com.sigmanote.notesshared

import android.os.Build
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

var datePatterns = listOf(
    DatePattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone("GMT"), 24),
    DatePattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", null, null),
    DatePattern("yyyy-MM-dd'T'HH:mm:ss.SSS", TimeZone.getDefault(), 23),
    DatePattern("yyyy-MM-dd", TimeZone.getDefault(), 10)
)

data class DatePattern(val pattern: String, val timeZone: TimeZone?, val length: Int?)

fun dateFor(date: String): Date {
    val dateFormat = SimpleDateFormat("", Locale.US)
    for (pattern in datePatterns) {
        if (pattern.length == null || date.length == pattern.length) {
            // Pattern character 'X' is only supported starting from API 24
            if (Build.VERSION.SDK_INT < 24 && pattern.pattern.contains("X")) {
                continue
            }
            if (pattern.timeZone != null) {
                dateFormat.timeZone = pattern.timeZone
            }
            dateFormat.applyPattern(pattern.pattern)
            return try {
                dateFormat.parse(date)
            } catch (e: ParseException) {
                null
            } ?: continue
        }
    }
    throw IllegalArgumentException("Invalid date literal")
}
