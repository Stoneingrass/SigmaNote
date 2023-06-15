

package com.sigmanote.notes.ui.notification

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sigmanote.notes.model.ReminderAlarmManager
import com.sigmanote.notes.model.entity.Note
import com.sigmanote.notes.ui.AssistedSavedStateViewModelFactory
import com.sigmanote.notes.ui.Event
import com.sigmanote.notes.ui.send
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class NotificationViewModel @AssistedInject constructor(
    @Assisted private val savedStateHandle: SavedStateHandle,
    private val notesRepository: NotesRepository,
    private val reminderAlarmManager: ReminderAlarmManager
) : ViewModel() {

    private val calendar = Calendar.getInstance()

    private var noteId: Long = Note.NO_ID

    private var postponeTime: Long = 0
        set(value) {
            field = value
            savedStateHandle[KEY_POSTPONE_TIME] = value
        }

    private val _showDateDialogEvent = MutableLiveData<Event<Long>>()
    val showDateDialogEvent: LiveData<Event<Long>>
        get() = _showDateDialogEvent

    private val _showTimeDialogEvent = MutableLiveData<Event<Long>>()
    val showTimeDialogEvent: LiveData<Event<Long>>
        get() = _showTimeDialogEvent

    private val _clearNotificationEvent = MutableLiveData<Event<Long>>()
    val clearNotificationEvent: LiveData<Event<Long>>
        get() = _clearNotificationEvent

    private val _exitEvent = MutableLiveData<Event<Unit>>()
    val exitEvent: LiveData<Event<Unit>>
        get() = _exitEvent

    init {
        noteId = savedStateHandle[KEY_NOTE_ID] ?: 0
        postponeTime = savedStateHandle[KEY_POSTPONE_TIME] ?: 0
    }

    fun onPostponeClicked(noteId: Long) {
        this.noteId = noteId
        savedStateHandle[KEY_NOTE_ID] = noteId

        viewModelScope.launch {
            val reminder = notesRepository.getNoteById(noteId)?.reminder
            if (reminder == null) {
                _exitEvent.send()
                return@launch
            }

            // Postpone by one hour by default
            calendar.timeInMillis = reminder.next.time
            calendar.add(Calendar.HOUR_OF_DAY, 1)
            postponeTime = calendar.timeInMillis

            _showDateDialogEvent.send(postponeTime)
        }
    }

    fun setPostponeDate(year: Int, month: Int, day: Int) {
        calendar.timeInMillis = postponeTime
        calendar.set(year, month, day)
        postponeTime = calendar.timeInMillis

        viewModelScope.launch {
            delay(INTER_DIALOG_DELAY)
            _showTimeDialogEvent.send(postponeTime)
        }
    }

    fun setPostponeTime(hour: Int, minute: Int) {
        calendar.timeInMillis = postponeTime
        calendar[Calendar.HOUR_OF_DAY] = hour
        calendar[Calendar.MINUTE] = minute

        if (calendar.timeInMillis > System.currentTimeMillis()) {
            viewModelScope.launch {
                val note = notesRepository.getNoteById(noteId)
                val reminder = note?.reminder
                if (note != null && reminder != null && reminder.recurrence == null
                    && !reminder.done && calendar.timeInMillis > reminder.next.time
                ) {
                    val newNote = note.copy(reminder = reminder.postponeTo(calendar.time))
                    notesRepository.updateNote(newNote)
                    reminderAlarmManager.setNoteReminderAlarm(newNote)
                }
                _clearNotificationEvent.send(noteId)
                _exitEvent.send()
            }
        } else {
            _clearNotificationEvent.send(noteId)
            _exitEvent.send()
        }
    }

    fun cancelPostpone() {
        _exitEvent.send()
    }

    @AssistedFactory
    interface Factory : AssistedSavedStateViewModelFactory<NotificationViewModel> {
        override fun create(savedStateHandle: SavedStateHandle): NotificationViewModel
    }

    companion object {
        private const val INTER_DIALOG_DELAY = 250L

        private const val KEY_NOTE_ID = "note_id"
        private const val KEY_POSTPONE_TIME = "postpone_time"
    }
}
