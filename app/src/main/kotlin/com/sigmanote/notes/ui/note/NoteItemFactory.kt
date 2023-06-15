

package com.sigmanote.notes.ui.note

import com.sigmanote.notes.model.PrefsManager
import com.sigmanote.notes.model.entity.Label
import com.sigmanote.notes.model.entity.ListNoteItem
import com.sigmanote.notes.model.entity.Note
import com.sigmanote.notes.ui.note.adapter.NoteItemList
import com.sigmanote.notes.ui.note.adapter.NoteItemText
import com.sigmanote.notes.ui.note.adapter.NoteListLayoutMode
import javax.inject.Inject

class NoteItemFactory @Inject constructor(
    private val prefs: PrefsManager
) {

    var query: String? = null

    var appendIdToTitle: Boolean = false

    fun createItem(
        note: Note,
        labels: List<Label>,
        checked: Boolean,
        showMarkAsDone: Boolean = false,
    ) = when (note.type) {
        NoteType.TEXT -> createTextItem(note, labels, checked, showMarkAsDone)
        NoteType.LIST -> createListItem(note, labels, checked, showMarkAsDone)
    }

    private fun createTextItem(
        note: Note,
        labels: List<Label>,
        checked: Boolean,
        showMarkAsDone: Boolean = false,
    ): NoteItemText {
        val title = createTitle(note)

        val contentTrim = note.content.trim()
        val content = if (query == null) {
            Highlighted(contentTrim)
        } else {
            val ellipsisThreshold = getStartEllipsisThreshold(START_ELLIPSIS_THRESHOLD_CONTENT) *
                    (prefs.getMaximumPreviewLines(NoteType.TEXT) - 1) + START_ELLIPSIS_THRESHOLD_CONTENT_FIRST
            val highlights = HighlightHelper.findHighlightsInString(contentTrim, query!!, MAX_HIGHLIGHTS_IN_TEXT)
            HighlightHelper.getStartEllipsizedText(contentTrim, highlights,
                ellipsisThreshold, START_ELLIPSIS_DISTANCE_CONTENT)
        }

        return NoteItemText(note.id, note, labels, checked, title, content, showMarkAsDone)
    }

    private fun createListItem(
        note: Note,
        labels: List<Label>,
        checked: Boolean,
        showMarkAsDone: Boolean,
    ): NoteItemList {

        val items = note.listItems  // already trimmed
        val noteItemCounts = items.size

        if (prefs.moveCheckedToBottom) {
            items.sortBy { it.checked }
        }

        val highlights = getListItemHighlights(items)
        val itemsCount = getListItemCount(items, highlights)

        var onlyCheckedInOverflow = true
        if (query != null) {
            val highlightIndices = highlights.mapIndexedNotNullTo(
                mutableListOf()) { i, h -> i.takeIf { h.isNotEmpty() } }
            highlightIndices += Int.MAX_VALUE
            var currHighlight = 0
            var delta = 0
            var i = 0
            while (i < items.size) {
                val highlightsLeft = highlightIndices.size - currHighlight - 1
                val itemSlotsLeft = itemsCount - i
                if (i == highlightIndices[currHighlight] - delta) {
                    currHighlight++
                } else if (itemSlotsLeft <= highlightsLeft) {
                    highlights.removeAt(i)
                    val item = items.removeAt(i)
                    onlyCheckedInOverflow = onlyCheckedInOverflow && item.checked
                    delta++
                    i--
                }
                i++
            }
        }
        val hiddenItems = items.subList(itemsCount, items.size)
        onlyCheckedInOverflow = onlyCheckedInOverflow && hiddenItems.all { it.checked }
        hiddenItems.clear()

        val overflowCount = noteItemCounts - items.size

        val title = createTitle(note)
        val ellipsisThreshold = getStartEllipsisThreshold(START_ELLIPSIS_THRESHOLD_ITEM)
        val highlightedItems = items.mapIndexed { i, item ->
            HighlightHelper.getStartEllipsizedText(item.content,
                if (query == null) mutableListOf() else highlights[i],
                ellipsisThreshold, START_ELLIPSIS_DISTANCE_ITEM)
        }
        val itemsChecked = items.map { it.checked }

        return NoteItemList(note.id, note, labels, checked, title, highlightedItems,
            itemsChecked, overflowCount, onlyCheckedInOverflow, showMarkAsDone)
    }

    private fun getListItemCount(items: List<ListNoteItem>, highlights: List<List<IntRange>>): Int {
        val maxItemsCount = prefs.getMaximumPreviewLines(NoteType.LIST)
        return minOf(maxItemsCount, if (prefs.moveCheckedToBottom) {
            var count = items.indexOfFirst { it.checked }
            if (count == -1) {
                count = items.size
            }
            if (query != null) {
                // Show checked items with highlights as well
                val checkedHighlighed = items.foldIndexed(0) { i, c, item ->
                    if (highlights[i].isNotEmpty() && item.checked) {
                        c + 1
                    } else {
                        c
                    }
                }
                count += checkedHighlighed
            }
            if (MINIMUM_LIST_NOTE_ITEMS in count until maxItemsCount) {
                count = minOf(items.size, MINIMUM_LIST_NOTE_ITEMS)
            }
            count
        } else {
            items.size
        })
    }

    private fun getListItemHighlights(items: List<ListNoteItem>): MutableList<MutableList<IntRange>> {
        return if (query == null) {
            mutableListOf()
        } else {
            var maxHighlights = MAX_HIGHLIGHTS_IN_LIST
            items.mapTo(mutableListOf()) {
                val ranges = HighlightHelper.findHighlightsInString(it.content, query!!,
                    minOf(maxHighlights, MAX_HIGHLIGHTS_IN_LIST_ITEM))
                maxHighlights -= ranges.size
                ranges
            }
        }
    }

    private fun createTitle(note: Note): Highlighted {
        var title = note.title.trim()
        if (appendIdToTitle) {
            title += " (${note.id})"
        }
        return if (query == null) {
            Highlighted(title)
        } else {
            val highlights = HighlightHelper.findHighlightsInString(title, query!!, MAX_HIGHLIGHTS_IN_TITLE)
            HighlightHelper.getStartEllipsizedText(title, highlights,
                getStartEllipsisThreshold(START_ELLIPSIS_THRESHOLD_TITLE), START_ELLIPSIS_DISTANCE_TITLE)
        }
    }

    // Start ellipsis threshold is doubled in list layout mode
    private fun getStartEllipsisThreshold(threshold: Int) =
        threshold * (if (prefs.listLayoutMode == NoteListLayoutMode.GRID) 1 else 2)

    companion object {
        private const val MINIMUM_LIST_NOTE_ITEMS = 2

        // Maximum number of highlights in each note region
        private const val MAX_HIGHLIGHTS_IN_TITLE = 2
        private const val MAX_HIGHLIGHTS_IN_TEXT = 10
        private const val MAX_HIGHLIGHTS_IN_LIST_ITEM = 2
        private const val MAX_HIGHLIGHTS_IN_LIST = 10
        private const val START_ELLIPSIS_THRESHOLD_TITLE = 20
        private const val START_ELLIPSIS_DISTANCE_TITLE = 10
        private const val START_ELLIPSIS_THRESHOLD_ITEM = 10
        private const val START_ELLIPSIS_DISTANCE_ITEM = 4
        private const val START_ELLIPSIS_THRESHOLD_CONTENT = 15  // per line of preview (-1)
        private const val START_ELLIPSIS_THRESHOLD_CONTENT_FIRST = 5  // for first line of preview
        private const val START_ELLIPSIS_DISTANCE_CONTENT = 20
    }
}
