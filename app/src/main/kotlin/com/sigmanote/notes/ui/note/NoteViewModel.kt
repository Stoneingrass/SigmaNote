
package com.sigmanote.notes.ui.note

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sigmanote.notes.model.PrefsManager
import com.sigmanote.notes.model.ReminderAlarmManager
import com.sigmanote.notes.model.entity.LabelRef
import com.sigmanote.notes.model.entity.Note
import com.sigmanote.notes.ui.Event
import com.sigmanote.notes.ui.ShareData
import com.sigmanote.notes.ui.note.adapter.MessageItem
import com.sigmanote.notes.ui.note.adapter.NoteAdapter
import com.sigmanote.notes.ui.note.adapter.NoteItem
import com.sigmanote.notes.ui.note.adapter.NoteListItem
import com.sigmanote.notes.ui.note.adapter.NoteListLayoutMode
import com.sigmanote.notes.ui.send
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Date

abstract class NoteViewModel(
    protected val savedStateHandle: SavedStateHandle,
    protected val notesRepository: NotesRepository,
    protected val labelsRepository: LabelsRepository,
    protected val prefs: PrefsManager,
    protected val noteItemFactory: NoteItemFactory,
    protected val reminderAlarmManager: ReminderAlarmManager,
) : ViewModel(), NoteAdapter.Callback {

    protected var listItems: List<NoteListItem> = emptyList()
        set(value) {
            field = value
            _noteItems.value = value

            _placeholderData.value = if (value.isEmpty()) {
                updatePlaceholder()
            } else {
                null
            }

            // Update selected notes.
            val selectedBefore = selectedNotes.size
            _selectedNotes.clear()
            selectedNoteIds.clear()
            for (item in value) {
                if (item is NoteItem && item.checked) {
                    _selectedNotes += item.note
                    selectedNoteIds += item.note.id
                }
            }

            updateNoteSelection()
            if (selectedNotes.size != selectedBefore) {
                saveNoteSelectionState()
            }
        }

    private val _selectedNotes = mutableSetOf<Note>()
    private val selectedNoteIds = mutableSetOf<Long>()
    protected val selectedNotes: Set<Note> get() = _selectedNotes

    protected abstract val selectedNoteStatus: NoteStatus?

    private val _noteItems = MutableLiveData<List<NoteListItem>>()
    val noteItems: LiveData<List<NoteListItem>>
        get() = _noteItems

    private val _listLayoutMode = MutableLiveData<NoteListLayoutMode>()
    val listLayoutMode: LiveData<NoteListLayoutMode>
        get() = _listLayoutMode

    private val _editItemEvent = MutableLiveData<Event<Pair<Long, Int>>>()
    val editItemEvent: LiveData<Event<Pair<Long, Int>>>
        get() = _editItemEvent

    private val _shareEvent = MutableLiveData<Event<ShareData>>()
    val shareEvent: LiveData<Event<ShareData>>
        get() = _shareEvent

    private val _statusChangeEvent = MutableLiveData<Event<StatusChange>>()
    val statusChangeEvent: LiveData<Event<StatusChange>>
        get() = _statusChangeEvent

    private val _currentSelection = MutableLiveData<NoteSelection>()
    val currentSelection: LiveData<NoteSelection>
        get() = _currentSelection

    private val _placeholderData = MutableLiveData<PlaceholderData?>(null)
    val placeholderData: LiveData<PlaceholderData?>
        get() = _placeholderData

    private val _showReminderDialogEvent = MutableLiveData<Event<List<Long>>>()
    val showReminderDialogEvent: LiveData<Event<List<Long>>>
        get() = _showReminderDialogEvent

    private val _showLabelsFragmentEvent = MutableLiveData<Event<List<Long>>>()
    val showLabelsFragmentEvent: LiveData<Event<List<Long>>>
        get() = _showLabelsFragmentEvent

    private val _showDeletedForeverConfirmEvent = MutableLiveData<Event<Unit>>()
    val showDeleteConfirmEvent: LiveData<Event<Unit>>
        get() = _showDeletedForeverConfirmEvent

    protected var noteListJob: Job? = null
    private var restoreStateJob: Job? = null

    init {
        // Initialize list layout to saved value.
        _listLayoutMode.value = prefs.listLayoutMode

        if (com.sigmanote.notes.BuildConfig.ENABLE_DEBUG_FEATURES) {
            noteItemFactory.appendIdToTitle = true
        }
    }

    protected open fun restoreState() {
        restoreStateJob = viewModelScope.launch {
            // Restore saved selected notes
            selectedNoteIds += savedStateHandle.get<List<Long>>(KEY_SELECTED_IDS)
                .orEmpty().toMutableSet()
            _selectedNotes += selectedNoteIds.mapNotNull { notesRepository.getNoteById(it) }
            updateNoteSelection()
            restoreStateJob = null
        }
    }

    protected suspend fun waitForRestoredState() {
        restoreStateJob?.join()
    }

    fun stopUpdatingList() {
        noteListJob?.cancel()
        noteListJob = null
    }

    abstract fun updatePlaceholder(): PlaceholderData

    fun clearSelection() {
        setAllSelected(false)
    }

    fun selectAll() {
        setAllSelected(true)
    }

    fun togglePin() {
        viewModelScope.launch {
            val newPinned = if (selectedNotes.any { it.pinned == PinnedStatus.UNPINNED }) {
                PinnedStatus.PINNED
            } else {
                PinnedStatus.UNPINNED
            }
            val newNotes = selectedNotes.mapNotNull { note ->
                if (note.pinned != PinnedStatus.CANT_PIN && note.pinned != newPinned) {
                    note.copy(pinned = newPinned)
                } else {
                    null
                }
            }
            notesRepository.updateNotes(newNotes)
        }
    }

    fun createReminder() {
        _showReminderDialogEvent.send(selectedNoteIds.toList())
    }

    fun changeLabels() {
        _showLabelsFragmentEvent.send(selectedNoteIds.toList())
    }

    protected open fun onListLayoutModeChanged() = Unit

    fun toggleListLayoutMode() {
        val mode = when (_listLayoutMode.value!!) {
            NoteListLayoutMode.LIST -> NoteListLayoutMode.GRID
            NoteListLayoutMode.GRID -> NoteListLayoutMode.LIST
        }
        _listLayoutMode.value = mode
        prefs.listLayoutMode = mode

        onListLayoutModeChanged()
    }

    fun moveSelectedNotes() {
        changeSelectedNotesStatus(if (selectedNoteStatus == NoteStatus.ACTIVE) {
            NoteStatus.ARCHIVED
        } else {
            NoteStatus.ACTIVE
        })
    }

    fun deleteSelectedNotesPre() {
        if (selectedNoteStatus == NoteStatus.DELETED) {
            // Ask user for confirmation before deleting selected notes forever.
            _showDeletedForeverConfirmEvent.send()
        } else {
            // Send to trash
            changeSelectedNotesStatus(NoteStatus.DELETED)
        }
    }

    fun deleteSelectedNotes() {
        // Delete forever
        viewModelScope.launch {
            notesRepository.deleteNotes(selectedNotes.toList())
            clearSelection()
        }
    }

    fun copySelectedNote(untitledName: String, copySuffix: String) {
        if (selectedNotes.size != 1) {
            return
        }

        viewModelScope.launch {
            val note = selectedNotes.first()
            val date = Date()
            val copy = note.copy(
                id = Note.NO_ID,
                title = Note.getCopiedNoteTitle(note.title, untitledName, copySuffix),
                addedDate = date,
                lastModifiedDate = date,
                reminder = null)
            val id = notesRepository.insertNote(copy)

            // Set labels for copy
            val labelIds = labelsRepository.getLabelIdsForNote(note.id)
            if (labelIds.isNotEmpty()) {
                labelsRepository.insertLabelRefs(labelIds.map { LabelRef(id, it) })
            }

            clearSelection()
        }
    }

    fun shareSelectedNote() {
        val note = selectedNotes.firstOrNull() ?: return
        _shareEvent.send(ShareData(note.title, note.asText()))
    }

    private fun setAllSelected(selected: Boolean) {
        if (!selected && selectedNotes.isEmpty()) {
            return
        }

        changeListItems {
            for ((i, item) in it.withIndex()) {
                if (item is NoteItem && item.checked != selected) {
                    it[i] = item.withChecked(selected)
                }
            }
        }
    }

    protected fun isNoteSelected(note: Note): Boolean {
        return note.id in selectedNoteIds
    }

    protected open fun updateNoteSelection() {
        val pinned = when {
            selectedNotes.none { it.status == NoteStatus.ACTIVE } -> PinnedStatus.CANT_PIN
            selectedNotes.any { it.pinned == PinnedStatus.UNPINNED } -> PinnedStatus.UNPINNED
            else -> PinnedStatus.PINNED
        }

        val hasReminder = selectedNotes.any { it.reminder != null }

        _currentSelection.value =
            NoteSelection(selectedNotes.size, selectedNoteStatus, pinned, hasReminder)
    }

    private fun saveNoteSelectionState() {
        savedStateHandle[KEY_SELECTED_IDS] = selectedNoteIds.toList()
    }

    protected fun changeNotesStatus(notes: Set<Note>, newStatus: NoteStatus) {
        val oldNotes = notes
            .filter { it.status != newStatus }
            .ifEmpty { return }

        viewModelScope.launch {
            val date = Date()
            val newNotes = mutableListOf<Note>()
            for (note in oldNotes) {
                newNotes += note.copy(status = newStatus,
                    pinned = if (newStatus == NoteStatus.ACTIVE) PinnedStatus.UNPINNED else PinnedStatus.CANT_PIN,
                    reminder = note.reminder.takeIf { newStatus != NoteStatus.DELETED },
                    lastModifiedDate = date)
                if (newStatus == NoteStatus.DELETED) {
                    if (note.reminder != null) {
                        // Remove reminder alarm for deleted note.
                        reminderAlarmManager.removeAlarm(note.id)
                    }
                }
            }

            notesRepository.updateNotes(newNotes)

            val statusChange = StatusChange(oldNotes, oldNotes.first().status, newStatus)
            _statusChangeEvent.send(statusChange)
        }
    }

    private fun changeSelectedNotesStatus(newStatus: NoteStatus) {
        changeNotesStatus(selectedNotes, newStatus)
        clearSelection()
    }

    override fun onNoteItemClicked(item: NoteItem, pos: Int) {
        if (selectedNotes.isEmpty()) {
            // Edit item
            _editItemEvent.send(Pair(item.note.id, pos))
        } else {
            // Toggle item selection
            toggleItemChecked(item, pos)
        }
    }

    override fun onNoteItemLongClicked(item: NoteItem, pos: Int) {
        toggleItemChecked(item, pos)
    }

    private fun toggleItemChecked(item: NoteItem, pos: Int) {
        changeListItems {
            it[pos] = item.withChecked(!item.checked)
        }
    }

    override fun onMessageItemDismissed(item: MessageItem, pos: Int) {
    }

    override fun onNoteActionButtonClicked(item: NoteItem, pos: Int) {
        // Do nothing.
    }

    override fun getNoteSwipeAction(direction: NoteAdapter.SwipeDirection) = SwipeAction.NONE

    override fun onNoteSwiped(pos: Int, direction: NoteAdapter.SwipeDirection) {
        // Do nothing.
    }

    override val strikethroughCheckedItems: Boolean
        get() = prefs.strikethroughChecked

    protected inline fun changeListItems(change: (MutableList<NoteListItem>) -> Unit) {
        val newList = listItems.toMutableList()
        change(newList)
        listItems = newList
    }

    data class NoteSelection(
        val count: Int,
        val status: NoteStatus?,
        val pinned: PinnedStatus,
        val hasReminder: Boolean
    )

    companion object {
        private const val KEY_SELECTED_IDS = "selected_ids"
    }
}
