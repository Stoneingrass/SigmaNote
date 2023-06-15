
@file:UseSerializers(DateTimeConverter::class, RecurrenceConverter::class)

package com.sigmanote.notes.model.entity

import com.sigmanote.notes.model.converter.DateTimeConverter
import com.sigmanote.notes.model.converter.RecurrenceConverter
import com.sigmanote.recurpicker.Recurrence
import com.sigmanote.recurpicker.RecurrenceFinder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.Date

@Serializable
data class Reminder(

    @SerialName("start")
    val start: Date,

    @SerialName("recurrence")
    @Serializable(with = RecurrenceConverter::class)
    val recurrence: Recurrence? = null,

    @SerialName("next")
    val next: Date,

    @SerialName("count")
    val count: Int,

    @SerialName("done")
    val done: Boolean
) {

    init {
        require(count > 0) { "Count must be greater than zero." }
        require(recurrence != null || count == 1) { "Count should be 1 if non-recurring." }
    }

    fun findNextReminder(recurrenceFinder: RecurrenceFinder): Reminder =
        if (recurrence == null) {
            // Not recurring.
            this
        } else {
            // Find next occurence based on the last.
            val found = recurrenceFinder.findBasedOn(recurrence, start.time, next.time,
                count, 1, includeStart = false)
            if (found.isEmpty()) {
                // Recurrence is done.
                this
            } else {
                copy(next = Date(found.first()), count = count + 1, done = false)
            }
        }

    fun postponeTo(date: Date): Reminder {
        require(recurrence == null) { "Cannot postpone recurring reminder." }
        require(!done) { "Cannot postpone reminder marked as done." }
        require(date.time > next.time) { "Postponed time must be after current time." }
        return copy(next = date)
    }

    fun markAsDone() = copy(done = true)

    class InvalidReminderException(message: String) : IllegalArgumentException(message)

    companion object {

        fun create(start: Date, recurrence: Recurrence?, recurrenceFinder: RecurrenceFinder): Reminder {
            val recur = recurrence.takeIf { it != Recurrence.DOES_NOT_REPEAT }
            val date = if (recur == null) {
                start
            } else {
                Date(recurrenceFinder.find(recur, start.time, 1).firstOrNull()
                    ?: throw InvalidReminderException("Recurring reminder has no events."))
            }
            return Reminder(start, recur, date, 1, false)
        }
    }
}
