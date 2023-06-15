
package com.sigmanote.notes.ui.note

import com.sigmanote.notes.R
import com.sigmanote.notes.model.ValueEnum
import com.sigmanote.notes.model.findValueEnum

enum class SwipeAction(override val value: String) : ValueEnum<String> {
    ARCHIVE("archive"),
    DELETE("delete"),
    NONE("none");

    companion object {
        fun fromValue(value: String): SwipeAction = findValueEnum(value)
    }
}
