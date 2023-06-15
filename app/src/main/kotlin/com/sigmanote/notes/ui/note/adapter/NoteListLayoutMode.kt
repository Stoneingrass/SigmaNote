

package com.sigmanote.notes.ui.note.adapter

import com.sigmanote.notes.model.ValueEnum
import com.sigmanote.notes.model.findValueEnum

enum class NoteListLayoutMode(override val value: Int) : ValueEnum<Int> {
    LIST(0),
    GRID(1);

    companion object {
        fun fromValue(value: Int): NoteListLayoutMode = findValueEnum(value)
    }
}
