

package com.sigmanote.notes.ui.reminder

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sigmanote.notes.model.ReminderAlarmManager
import com.sigmanote.notes.model.entity.Reminder
import com.sigmanote.notes.setToStartOfDay
import com.sigmanote.notes.ui.AssistedSavedStateViewModelFactory
import com.sigmanote.notes.ui.Event
import com.sigmanote.notes.ui.send
import com.sigmanote.recurpicker.Recurrence
import com.sigmanote.recurpicker.RecurrenceFinder
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class ReminderViewModel @AssistedInject constructor(
    @Assisted private val savedStateHandle: SavedStateHandle,
    private val notesRepository: NotesRepository,
    private val reminderAlarmManager: ReminderAlarmManager
) : ViewModel() {

    private val calendar = Calendar.getInstance()

    private var noteIds = emptyList<Long>()
    private var date: Long   // UTC millis
    private var recurrence: Recurrence

    private val _details = MutableLiveData<ReminderDetails>()
    val details: LiveData<ReminderDetails>
        get() = _details

    private val _invalidTime = MutableLiveData<Boolean>()
    val invalidTime: LiveData<Boolean>
        get() = _invalidTime

    private val _isEditingReminder = MutableLiveData<Boolean>()
    val isEditingReminder: LiveData<Boolean>
        get() = _isEditingReminder

    private val _isDeleteBtnVisible = MutableLiveData<Boolean>()
    val isDeleteBtnVisible: LiveData<Boolean>
        get() = _isDeleteBtnVisible

    private val _showDateDialogEvent = MutableLiveData<Event<Long>>()
    val showDateDialogEvent: LiveData<Event<Long>>
        get() = _showDateDialogEvent

    private val _showTimeDialogEvent = MutableLiveData<Event<Long>>()
    val showTimeDialogEvent: LiveData<Event<Long>>
        get() = _showTimeDialogEvent

    private val _showRecurrenceListDialogEvent = MutableLiveData<Event<ReminderDetails>>()
    val showRecurrenceListDialogEvent: LiveData<Event<ReminderDetails>>
        get() = _showRecurrenceListDialogEvent

    private val _showRecurrencePickerDialogEvent = MutableLiveData<Event<ReminderDetails>>()
    val showRecurrencePickerDialogEvent: LiveData<Event<ReminderDetails>>
        get() = _showRecurrencePickerDialogEvent

    private val _reminderChangeEvent = MutableLiveData<Event<Reminder?>>()
    val reminderChangeEvent: LiveData<Event<Reminder?>>
        get() = _reminderChangeEvent

    private val _dismissEvent = MutableLiveData<Event<Unit>>()
    val dismissEvent: LiveData<Event<Unit>>
        get() = _dismissEvent

    init {
        noteIds = savedStateHandle.get<List<Long>>(KEY_NOTE_IDS).orEmpty()
        date = savedStateHandle[KEY_DATE] ?: Long.MAX_VALUE
        recurrence = savedStateHandle[KEY_RECURRENCE] ?: Recurrence.DOES_NOT_REPEAT

        checkIfTimeIsValid()
        updateReminderDetails()
    }

    fun start(noteIds: List<Long>) {
        require(noteIds.isNotEmpty()) { "No notes to change reminder for." }

        if (this.noteIds.isNotEmpty()) {
            // Already started.
            return
        }

        viewModelScope.launch {
            this@ReminderViewModel.noteIds = noteIds
            savedStateHandle[KEY_NOTE_IDS] = noteIds

            val reminder = if (noteIds.size == 1) {
                notesRepository.getNoteById(noteIds.first())?.reminder
            } else {
                null
            }

            _isEditingReminder.value = reminder != null

            _isDeleteBtnVisible.value = false
            for (noteId in noteIds) {
                val note = notesRepository.getNoteById(noteId) ?: continue
                if (note.reminder != null) {
                    _isDeleteBtnVisible.value = true
                    break
                }
            }

            if (reminder != null) {
                date = reminder.next.time
                recurrence = reminder.recurrence ?: Recurrence.DOES_NOT_REPEAT
            } else {
                calendar.timeInMillis = System.currentTimeMillis()
                val currHour = calendar[Calendar.HOUR_OF_DAY]
                val todayReminderHour = DEFAULT_REMINDER_HOURS.find { it > currHour + REMINDER_HOUR_MIN_DISTANCE }
                calendar.setToStartOfDay()
                calendar[Calendar.HOUR_OF_DAY] = todayReminderHour
                    ?: DEFAULT_REMINDER_HOURS.first { it > currHour + REMINDER_HOUR_MIN_DISTANCE - HOURS_IN_DAY }
                if (todayReminderHour == null) {
                    // All preset hours past for today, use first preset for tomorrow
                    calendar.add(Calendar.DATE, 1)
                }

                date = calendar.timeInMillis
                recurrence = Recurrence.DOES_NOT_REPEAT
            }

            savedStateHandle[KEY_DATE] = date
            savedStateHandle[KEY_RECURRENCE] = recurrence

            checkIfTimeIsValid()
            updateReminderDetails()
        }
    }

    private fun dismiss() {
        noteIds = emptyList()
        _dismissEvent.send()
    }

    fun onDateClicked() {
        _showDateDialogEvent.send(date)
    }

    fun changeDate(year: Int, month: Int, day: Int) {
        calendar.timeInMillis = date
        calendar.set(year, month, day)

        date = calendar.timeInMillis
        savedStateHandle[KEY_DATE] = date

        checkIfTimeIsValid()
        updateRecurrenceForDate()
        updateReminderDetails()
    }

    private fun updateRecurrenceForDate() {
        if (recurrence.byMonthDay == -1 &&
            calendar[Calendar.DATE] != calendar.getActualMaximum(Calendar.MONTH)
        ) {
            recurrence = Recurrence(recurrence) { dayInMonth = 0 }
        }

        // Remove end date if before start date.
        if (recurrence.endDate.compareDateTo(date) < 0) {
            recurrence = Recurrence(recurrence) { endType = Recurrence.EndType.NEVER }
        }

        savedStateHandle[KEY_RECURRENCE] = recurrence
    }

    fun onTimeClicked() {
        _showTimeDialogEvent.send(date)
    }

    fun changeTime(hour: Int, minute: Int) {
        calendar.timeInMillis = date
        calendar[Calendar.HOUR_OF_DAY] = hour
        calendar[Calendar.MINUTE] = minute

        date = calendar.timeInMillis
        savedStateHandle[KEY_DATE] = date

        checkIfTimeIsValid()
        updateReminderDetails()
    }

    fun onRecurrenceClicked() {
        _showRecurrenceListDialogEvent.send(details.value!!)
    }

    fun onRecurrenceCustomClicked() {
        _showRecurrencePickerDialogEvent.send(details.value!!)
    }

    fun changeRecurrence(recurrence: Recurrence) {
        this.recurrence = recurrence
        savedStateHandle[KEY_RECURRENCE] = recurrence

        updateReminderDetails()
    }

    fun createReminder() {
        checkIfTimeIsValid()
        if (invalidTime.value == false) {
            val reminder = try {
                Reminder.create(Date(date), recurrence, RecurrenceFinder())
            } catch (e: Reminder.InvalidReminderException) {
                null
            }
            viewModelScope.launch {
                changeReminder(reminder)
                dismiss()
            }
        }
    }

    fun deleteReminder() {
        viewModelScope.launch {
            changeReminder(null)
            dismiss()
        }
    }

    private suspend fun changeReminder(reminder: Reminder?) {
        val newNotes = noteIds.mapNotNull { id ->
            val oldNote = notesRepository.getNoteById(id)
            if (oldNote == null || oldNote.reminder == reminder) {
                null
            } else {
                oldNote.copy(reminder = reminder)
            }
        }
        notesRepository.updateNotes(newNotes)

        // Update alarms
        for (note in newNotes) {
            reminderAlarmManager.setNoteReminderAlarm(note)
        }

        _reminderChangeEvent.send(reminder)
    }

    private fun checkIfTimeIsValid() {
        _invalidTime.value = date.compareDateTimeTo(System.currentTimeMillis()) <= 0
    }

    private fun updateReminderDetails() {
        _details.value = if (noteIds.isEmpty()) {
            ReminderDetails(System.currentTimeMillis(), Recurrence.DOES_NOT_REPEAT)
        } else {
            ReminderDetails(date, recurrence)
        }
    }

    data class ReminderDetails(val date: Long, val recurrence: Recurrence)

    @AssistedFactory
    interface Factory : AssistedSavedStateViewModelFactory<ReminderViewModel> {
        override fun create(savedStateHandle: SavedStateHandle): ReminderViewModel
    }

    private fun Long.compareDateTo(other: Long): Int {
        calendar.timeInMillis = this
        val year1 = calendar[Calendar.YEAR]
        val day1 = calendar[Calendar.DAY_OF_YEAR]

        calendar.timeInMillis = other
        val year2 = calendar[Calendar.YEAR]
        val day2 = calendar[Calendar.DAY_OF_YEAR]

        return when {
            year1 > year2 -> 1
            year1 < year2 -> -1
            day1 > day2 -> 1
            day1 < day2 -> -1
            else -> 0
        }
    }

    private fun Long.compareDateTimeTo(other: Long): Int {
        val dateComp = this.compareDateTo(other)
        if (dateComp != 0) return dateComp

        calendar.timeInMillis = this
        val hour1 = calendar[Calendar.HOUR_OF_DAY]
        val min1 = calendar[Calendar.MINUTE]

        calendar.timeInMillis = other
        val hour2 = calendar[Calendar.HOUR_OF_DAY]
        val min2 = calendar[Calendar.MINUTE]

        return when {
            hour1 > hour2 -> 1
            hour1 < hour2 -> -1
            min1 > min2 -> 1
            min1 < min2 -> -1
            else -> 0
        }
    }

    companion object {
        private val DEFAULT_REMINDER_HOURS = listOf(8, 13, 18, 20)
        private const val REMINDER_HOUR_MIN_DISTANCE = 3

        private const val HOURS_IN_DAY = 24

        private const val KEY_DATE = "date"
        private const val KEY_RECURRENCE = "recurrence"
        private const val KEY_NOTE_IDS = "note_ids"
    }
}
