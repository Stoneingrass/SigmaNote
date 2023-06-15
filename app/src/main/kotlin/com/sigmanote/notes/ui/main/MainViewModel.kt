
package com.sigmanote.notes.ui.main

import android.view.Menu
import android.view.MenuItem
import androidx.core.view.contains
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDirections
import com.sigmanote.notes.NavGraphMainDirections
import com.sigmanote.notes.R
import com.sigmanote.notes.model.JsonManager
import com.sigmanote.notes.model.PrefsManager
import com.sigmanote.notes.model.ReminderAlarmManager
import com.sigmanote.notes.model.entity.Label
import com.sigmanote.notes.ui.AssistedSavedStateViewModelFactory
import com.sigmanote.notes.ui.Event
import com.sigmanote.notes.ui.home.HomeFragmentDirections
import com.sigmanote.notes.ui.navigation.HomeDestination
import com.sigmanote.notes.ui.send
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.io.OutputStream
import kotlin.time.Duration.Companion.hours

class MainViewModel @AssistedInject constructor(
    private val notesRepository: NotesRepository,
    private val labelsRepository: LabelsRepository,
    private val prefsManager: PrefsManager,
    private val jsonManager: JsonManager,
    private val reminderAlarmManager: ReminderAlarmManager,
    @Assisted savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _editNoteEvent = MutableLiveData<Event<Long>>()
    val editItemEvent: LiveData<Event<Long>>
        get() = _editNoteEvent

    private val _autoExportEvent = MutableLiveData<Event<String>>()
    val autoExportEvent: LiveData<Event<String>>
        get() = _autoExportEvent

    private val _createNoteEvent = MutableLiveData<Event<NewNoteData>>()
    val createNoteEvent: LiveData<Event<NewNoteData>>
        get() = _createNoteEvent


    private val _deletionFinishedSignal = Semaphore(1, 1)

    private val _navDirectionsEvent = MutableLiveData<Event<NavDirections>>()
    val navDirectionsEvent: LiveData<Event<NavDirections>>
        get() = _navDirectionsEvent

    private val _currentHomeDestination = savedStateHandle.getLiveData<HomeDestination>(
        KEY_HOME_DESTINATION, HomeDestination.Status(NoteStatus.ACTIVE))
    val currentHomeDestination: LiveData<HomeDestination>
        get() = _currentHomeDestination

    private val _drawerCloseEvent = MutableLiveData<Event<Unit>>()
    val drawerCloseEvent: LiveData<Event<Unit>>
        get() = _drawerCloseEvent

    private val _clearLabelsEvent = MutableLiveData<Event<Unit>>()
    val clearLabelsEvent: LiveData<Event<Unit>>
        get() = _clearLabelsEvent

    private val _labelsAddEvent = MutableLiveData<Event<List<Label>?>>()
    val labelsAddEvent: LiveData<Event<List<Label>?>>
        get() = _labelsAddEvent

    private val _manageLabelsVisibility = MutableLiveData<Boolean>()
    val manageLabelsVisibility: LiveData<Boolean>
        get() = _manageLabelsVisibility

    private var labelsJob: Job? = null

    init {
        viewModelScope.launch {
            if (prefsManager.shouldAutoExport && prefsManager.autoExportUri == PrefsManager.AUTO_EXPORT_NO_URI) {

                prefsManager.disableAutoExport()
            }


            reminderAlarmManager.updateAllAlarms()


            val lastCreatedNote = notesRepository.getLastCreatedNote()
            if (lastCreatedNote?.isBlank == true) {
                notesRepository.deleteNote(lastCreatedNote)
            }
            _deletionFinishedSignal.release()


            while (true) {
                notesRepository.deleteOldNotesInTrash()

                if (prefsManager.shouldAutoExport &&
                    System.currentTimeMillis() - prefsManager.lastAutoExportTime >
                    PrefsManager.AUTO_EXPORT_DELAY.inWholeMilliseconds
                ) {
                    _autoExportEvent.send(prefsManager.autoExportUri)
                }

                delay(PERIODIC_TASK_INTERVAL.inWholeMilliseconds)
            }
        }
    }

    fun onStart() {
        viewModelScope.launch {
            notesRepository.deleteOldNotesInTrash()
        }
    }

    fun startPopulatingDrawerWithLabels() {
        labelsJob?.cancel()

        labelsJob = viewModelScope.launch {
            var oldLabelsList: List<Label> = listOf()
            labelsRepository.getAllLabelsByUsage().collect { labelsList ->
                if (oldLabelsList != labelsList) {
                    oldLabelsList = labelsList


                    if (_currentHomeDestination.value is HomeDestination.Labels) {
                        if ((_currentHomeDestination.value as HomeDestination.Labels).label !in labelsList) {
                            _currentHomeDestination.value = HomeDestination.Status(NoteStatus.ACTIVE)
                        }
                    }


                    _clearLabelsEvent.send()
                    _labelsAddEvent.send(labelsList)
                    _manageLabelsVisibility.value = labelsList.isNotEmpty()
                }
            }
        }
    }

    fun selectLabel(label: Label) {
        _currentHomeDestination.value = HomeDestination.Labels(label)
    }

    fun navigationItemSelected(item: MenuItem, labelsMenu: Menu) {
        _drawerCloseEvent.send()

        when (item.itemId) {
            R.id.drawer_item_notes -> {
                _currentHomeDestination.value = HomeDestination.Status(NoteStatus.ACTIVE)
            }
            R.id.drawer_item_reminders -> {
                _currentHomeDestination.value = HomeDestination.Reminders
            }
            R.id.drawer_item_create_label -> {
                _navDirectionsEvent.send(HomeFragmentDirections.actionHomeToLabelEdit())
            }
            R.id.drawer_item_edit_labels -> {
                _navDirectionsEvent.send(NavGraphMainDirections.actionLabel(longArrayOf()))
            }
            R.id.drawer_item_archived -> {
                _currentHomeDestination.value = HomeDestination.Status(NoteStatus.ARCHIVED)
            }
            R.id.drawer_item_deleted -> {
                _currentHomeDestination.value = HomeDestination.Status(NoteStatus.DELETED)
            }
            R.id.drawer_item_settings -> {
                _navDirectionsEvent.send(HomeFragmentDirections.actionHomeToSettings())
            }
        }


        if (labelsMenu.contains(item)) {
            viewModelScope.launch {
                val label = labelsRepository.getLabelByName(item.title as String)
                if (label != null) {
                    selectLabel(label)
                }
            }
        }
    }

    fun createNote(type: NoteType, title: String = "", content: String = "") {
        viewModelScope.launch {

            _deletionFinishedSignal.acquire()
            _createNoteEvent.send(NewNoteData(type, title, content))
        }
    }

    fun editNote(id: Long) {
        viewModelScope.launch {

            if (notesRepository.getNoteById(id) != null) {
                _editNoteEvent.send(id)
            }
        }
    }

    fun autoExport(output: OutputStream?) {
        if (output != null) {
            viewModelScope.launch(Dispatchers.IO) {
                prefsManager.autoExportFailed = try {
                    val jsonData = jsonManager.exportJsonData()
                    output.use {
                        output.write(jsonData.toByteArray())
                    }
                    prefsManager.lastAutoExportTime = System.currentTimeMillis()
                    false
                } catch (e: Exception) {
                    true
                }
            }
        } else {
            prefsManager.autoExportFailed = true
        }
    }

    @AssistedFactory
    interface Factory : AssistedSavedStateViewModelFactory<MainViewModel> {
        override fun create(savedStateHandle: SavedStateHandle): MainViewModel
    }

    data class NewNoteData(val type: NoteType, val title: String, val content: String)

    companion object {
        private const val KEY_HOME_DESTINATION = "destination"

        private val PERIODIC_TASK_INTERVAL = 1.hours
    }
}
