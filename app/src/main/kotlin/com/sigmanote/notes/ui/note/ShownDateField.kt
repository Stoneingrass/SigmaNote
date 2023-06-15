
package com.sigmanote.notes.ui.note

import com.sigmanote.notes.R
import com.sigmanote.notes.model.ValueEnum
import com.sigmanote.notes.model.findValueEnum

enum class ShownDateField(override val value: String) : ValueEnum<String> {
    ADDED("added"),
    MODIFIED("modified"),
    NONE("none");

    companion object {
        fun fromValue(value: String): ShownDateField = findValueEnum(value)
    }
}
