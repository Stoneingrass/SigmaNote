
package com.sigmanote.notes.ui.notification

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.sigmanote.notes.App
import com.sigmanote.notes.R
import com.sigmanote.notes.model.entity.Note
import com.sigmanote.notes.receiver.AlarmReceiver
import com.sigmanote.notes.ui.navGraphViewModel
import com.sigmanote.notes.ui.observeEvent
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

class NotificationActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: NotificationViewModel.Factory
    private val viewModel by navGraphViewModel(R.id.nav_graph_notification) { viewModelFactory.create(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (applicationContext as App).appComponent.inject(this)

        setContentView(R.layout.activity_notification)

        setupViewModelObservers()

        if (savedInstanceState != null) {
            val timePicker = supportFragmentManager.findFragmentByTag(TIME_DIALOG_TAG) as MaterialTimePicker?
            if (timePicker != null) {
                timePicker.clearOnPositiveButtonClickListeners()
                registerTimePickerListener(timePicker)
            }

            @Suppress("UNCHECKED_CAST")
            val datePicker = supportFragmentManager.findFragmentByTag(DATE_DIALOG_TAG) as MaterialDatePicker<Long>?
            if (datePicker != null) {
                datePicker.clearOnPositiveButtonClickListeners()
                registerDatePickerListener(datePicker)
            }
        }

        handleIntent(intent)
    }

    private fun setupViewModelObservers() {

        viewModel.showDateDialogEvent.observeEvent(this) { date ->
            val calendarConstraints = CalendarConstraints.Builder()
                .setStart(System.currentTimeMillis())
                .setValidator(DateValidatorPointForward.now())
                .build()

            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setCalendarConstraints(calendarConstraints)
                .setSelection(date + TimeZone.getDefault().getOffset(date))
                .build()

            registerDatePickerListener(datePicker)
            datePicker.addOnNegativeButtonClickListener {
                viewModel.cancelPostpone()
            }
            datePicker.show(supportFragmentManager, DATE_DIALOG_TAG)
        }

        viewModel.showTimeDialogEvent.observeEvent(this) { date ->
            val isUsing24HourFormat = DateFormat.is24HourFormat(this)
            val timeFormat = if (isUsing24HourFormat) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = date

            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(timeFormat)
                .setHour(calendar[Calendar.HOUR_OF_DAY])
                .setMinute(calendar[Calendar.MINUTE])
                .build()

            registerTimePickerListener(timePicker)
            timePicker.addOnNegativeButtonClickListener {
                viewModel.cancelPostpone()
            }
            timePicker.show(supportFragmentManager, TIME_DIALOG_TAG)
        }

        viewModel.clearNotificationEvent.observeEvent(this) { noteId ->
            NotificationManagerCompat.from(this).cancel(noteId.toInt())
        }

        viewModel.exitEvent.observeEvent(this) {
            finish()
        }
    }

    private fun handleIntent(intent: Intent) {
        if (!intent.getBooleanExtra(KEY_INTENT_HANDLED, false)) {
            when (intent.action) {
                INTENT_ACTION_POSTPONE -> {
                    val noteId = intent.getLongExtra(AlarmReceiver.EXTRA_NOTE_ID, Note.NO_ID)
                    viewModel.onPostponeClicked(noteId)
                }
            }

            intent.putExtra(KEY_INTENT_HANDLED, true)
        }
    }

    private fun registerDatePickerListener(picker: MaterialDatePicker<Long>) {
        picker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selection - TimeZone.getDefault().getOffset(selection)
            viewModel.setPostponeDate(calendar[Calendar.YEAR],
                calendar[Calendar.MONTH],
                calendar[Calendar.DAY_OF_MONTH])
        }
    }

    private fun registerTimePickerListener(picker: MaterialTimePicker) {
        picker.addOnPositiveButtonClickListener {
            viewModel.setPostponeTime(picker.hour, picker.minute)
        }
    }

    companion object {
        private const val DATE_DIALOG_TAG = "date-picker-dialog"
        private const val TIME_DIALOG_TAG = "time-picker-dialog"

        private const val KEY_INTENT_HANDLED = "com.sigmanote.notes.INTENT_HANDLED"

        const val INTENT_ACTION_POSTPONE = "com.sigmanote.notes.reminder.POSTPONE"
    }
}
