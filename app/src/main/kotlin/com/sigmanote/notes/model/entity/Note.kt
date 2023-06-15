

package com.sigmanote.notes.model.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.sigmanote.notes.model.converter.NoteMetadataConverter
import debugCheck
import debugRequire
import kotlinx.serialization.Transient
import java.util.Date

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    @Transient
    val id: Long = NO_ID,

    @ColumnInfo(name = "type")
    val type: NoteType,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "metadata")
    val metadata: NoteMetadata,

    @ColumnInfo(name = "added_date")
    val addedDate: Date,

    @ColumnInfo(name = "modified_date")
    val lastModifiedDate: Date,

    @ColumnInfo(name = "status")
    val status: NoteStatus,

    @ColumnInfo(name = "pinned")
    val pinned: PinnedStatus,

    /**
     * The note reminder, or `null` if none is set.
     */
    @Embedded(prefix = "reminder_")
    val reminder: Reminder?,
) {

    init {
        require(when (type) {
            NoteType.TEXT -> metadata is BlankNoteMetadata
            NoteType.LIST -> metadata is ListNoteMetadata
        })

        debugRequire(addedDate.time <= lastModifiedDate.time) {
            "Note added date must be before or on last modified date."
        }

        debugRequire(status != NoteStatus.ACTIVE || pinned != PinnedStatus.CANT_PIN) {
            "Active note must be pinnable."
        }
        debugRequire(status == NoteStatus.ACTIVE || pinned == PinnedStatus.CANT_PIN) {
            "Archived or deleted note must not be pinnable."
        }

        debugRequire(status != NoteStatus.DELETED || reminder == null) {
            "Deleted note cannot have a reminder."
        }
    }

    val isBlank: Boolean
        get() = title.isBlank() && content.isBlank() && reminder == null

    val listItems: MutableList<ListNoteItem>
        get() {
            check(type == NoteType.LIST) { "Cannot get list items for non-list note." }

            val checked = (metadata as ListNoteMetadata).checked
            val items = content.split('\n')
            if (items.size == 1 && checked.isEmpty()) {
                // No items
                return mutableListOf()
            }

            debugCheck(checked.size == items.size) { "Invalid list note data." }

            return items.mapIndexedTo(mutableListOf()) { i, text ->
                ListNoteItem(text.trim(), checked.getOrElse(i) { false })
            }
        }

    fun asTextNote(keepCheckedItems: Boolean): Note = when (type) {
        NoteType.TEXT -> this
        NoteType.LIST -> {
            val items = listItems
            val content = if (items.all { it.content.isBlank() }) {
                // All list items are blank, so no content.
                ""
            } else {
                // Append a bullet point to each line of content.
                buildString {
                    for (item in items) {
                        if (keepCheckedItems || !item.checked) {
                            append(DEFAULT_BULLET_CHAR)
                            append(' ')
                            appendLine(item.content)
                        }
                    }
                    if (length > 0) {
                        deleteCharAt(lastIndex)
                    }
                }
            }
            copy(type = NoteType.TEXT, content = content, metadata = BlankNoteMetadata)
        }
    }

    fun asListNote(): Note = when (type) {
        NoteType.LIST -> this
        NoteType.TEXT -> {
            // Convert each list item to a text line.
            val text = content.trim()
            val lines = text.split('\n')
            val content = if (lines.all { it.isNotEmpty() && it.first() in BULLET_CHARS }) {
                // All lines start with a bullet point, remove them.
                buildString {
                    for (line in lines) {
                        appendLine(line.substring(1).trim())
                    }
                    deleteCharAt(lastIndex)
                }
            } else {
                // List note items content are separated by line breaks, and this is already the case.
                text
            }
            val metadata = ListNoteMetadata(List(lines.size) { false })
            copy(type = NoteType.LIST, content = content, metadata = metadata)
        }
    }

    fun asText(includeTitle: Boolean = true): String {
        val textNote = asTextNote(true)
        return buildString {
            if (includeTitle && title.isNotBlank()) {
                appendLine(textNote.title)
            }
            append(textNote.content)
        }
    }

    companion object {
        const val NO_ID = 0L

        const val BULLET_CHARS = "-+*•–"
        const val DEFAULT_BULLET_CHAR = '-'

        fun getCopiedNoteTitle(
            currentTitle: String,
            untitledName: String,
            copySuffix: String
        ): String {
            val match = "^(.*) - $copySuffix(?:\\s+([1-9]\\d*))?$".toRegex().find(currentTitle)
            return when {
                match != null -> {
                    val name = match.groupValues[1]
                    val number = (match.groupValues[2].toIntOrNull() ?: 1) + 1
                    "$name - $copySuffix $number"
                }
                currentTitle.isBlank() -> "$untitledName - $copySuffix"
                else -> "$currentTitle - $copySuffix"
            }
        }
    }
}

data class NoteWithLabels(
    @Embedded
    val note: Note,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            LabelRef::class,
            parentColumn = "noteId",
            entityColumn = "labelId",
        ))
    val labels: List<Label>
)

data class ListNoteItem(val content: String, val checked: Boolean) {

    init {
        require('\n' !in content) { "List item content cannot contain line breaks." }
    }
}
