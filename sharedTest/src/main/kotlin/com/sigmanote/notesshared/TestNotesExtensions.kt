
@file:Suppress("LongParameterList")

package com.sigmanote.notesshared

import com.sigmanote.notes.model.entity.BlankNoteMetadata
import com.sigmanote.notes.model.entity.ListNoteItem
import com.sigmanote.notes.model.entity.ListNoteMetadata
import com.sigmanote.notes.model.entity.Note
import com.sigmanote.notes.model.entity.NoteMetadata
import com.sigmanote.notes.model.entity.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.Date

fun testNote(
    id: Long = Note.NO_ID,
    type: NoteType = NoteType.TEXT,
    title: String = "note",
    content: String = "content",
    metadata: NoteMetadata = BlankNoteMetadata,
    added: Date = Date(),
    modified: Date = added,
    status: NoteStatus = NoteStatus.ACTIVE,
    pinned: PinnedStatus = defaultPinnedStatusForStatus(status),
    reminder: Reminder? = null
) = Note(id, type, title, content, metadata, added, modified, status, pinned, reminder)

fun listNote(
    items: List<ListNoteItem>,
    id: Long = Note.NO_ID,
    title: String = "note",
    added: Date = Date(),
    modified: Date = added,
    status: NoteStatus = NoteStatus.ACTIVE,
    pinned: PinnedStatus = defaultPinnedStatusForStatus(status),
    reminder: Reminder? = null
) = Note(id, NoteType.LIST, title,
    items.joinToString("\n") {
        require('\n' !in it.content)
        it.content
    },
    ListNoteMetadata(items.map { it.checked }), added, modified, status, pinned, reminder)

fun assertNoteEquals(
    expected: Note,
    actual: Note,
    dateEpsilon: Long = 1000,
    ignoreId: Boolean = true
) {
    assertTrue("Notes have different added dates.",
        (expected.addedDate.time - actual.addedDate.time) < dateEpsilon)
    assertTrue("Notes have different last modified dates.",
        (expected.lastModifiedDate.time - actual.lastModifiedDate.time) < dateEpsilon)
    assertEquals(expected, actual.copy(
        id = if (ignoreId) expected.id else actual.id,
        addedDate = expected.addedDate,
        lastModifiedDate = expected.lastModifiedDate))
}

private fun defaultPinnedStatusForStatus(status: NoteStatus) =
    if (status == NoteStatus.ACTIVE) PinnedStatus.UNPINNED else PinnedStatus.CANT_PIN
