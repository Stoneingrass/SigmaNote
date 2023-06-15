

package com.sigmanote.notes.model

import com.sigmanote.notes.OpenForTesting
import com.sigmanote.notes.model.entity.Note
import com.sigmanote.recurpicker.RecurrenceFinder
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject

@OpenForTesting
class ReminderAlarmManager @Inject constructor(
    private val notesRepository: NotesRepository,
    private val alarmCallback: ReminderAlarmCallback
) {

    private val recurrenceFinder = RecurrenceFinder()

    suspend fun updateAllAlarms() {
        val updatedNotes = mutableListOf<Note>()
        for (note in notesRepository.getNotesWithReminder().first()) {
            updatedNotes += setNextNoteReminderAlarmInternal(note.note) ?: continue
        }
        notesRepository.updateNotes(updatedNotes)
    }

    fun setNoteReminderAlarm(note: Note) {
        val reminder = note.reminder
        if (reminder != null) {
            alarmCallback.addAlarm(note.id, reminder.next.time)
        } else {
            alarmCallback.removeAlarm(note.id)
        }
    }

    suspend fun setNextNoteReminderAlarm(note: Note) {
        val updatedNote = setNextNoteReminderAlarmInternal(note)
        if (updatedNote != null) {
            notesRepository.updateNote(updatedNote)
        }
    }

    private fun setNextNoteReminderAlarmInternal(note: Note): Note? {
        // Update note in database if reminder is recurring
        val now = Date()
        var reminder = note.reminder ?: return null

        // For recurring reminders, skip all past events and
        // find first event that hasn't happened yet, or last event.
        while (reminder.next.before(now)) {
            val nextReminder = reminder.findNextReminder(recurrenceFinder)
            if (nextReminder !== reminder) {
                reminder = nextReminder
            } else {
                // Recurrence done, or not recurring.
                // Reminder will appear as overdue.
                break
            }
        }
        if (reminder.next.after(now)) {
            alarmCallback.addAlarm(note.id, reminder.next.time)
        } else {
            alarmCallback.removeAlarm(note.id)
        }

        return if (reminder !== note.reminder) {
            // Reminder changed, update note in database.
            note.copy(reminder = reminder)
        } else {
            null
        }
    }

    suspend fun markReminderAsDone(noteId: Long) {
        val note = notesRepository.getNoteById(noteId) ?: return
        notesRepository.updateNote(note.copy(reminder = note.reminder?.markAsDone()))
    }

    fun removeAlarm(noteId: Long) {
        alarmCallback.removeAlarm(noteId)
    }

    suspend fun removeAllAlarms() {
        val notes = notesRepository.getNotesWithReminder().first()
        for (note in notes) {
            removeAlarm(note.note.id)
        }
    }
}

interface ReminderAlarmCallback {
    fun addAlarm(noteId: Long, time: Long)
    fun removeAlarm(noteId: Long)
}
