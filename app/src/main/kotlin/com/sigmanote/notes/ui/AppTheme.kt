
package com.sigmanote.notes.ui

import com.sigmanote.notes.R
import com.sigmanote.notes.model.ValueEnum
import com.sigmanote.notes.model.findValueEnum

enum class AppTheme(override val value: String) : ValueEnum<String> {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        fun fromValue(value: String): AppTheme = findValueEnum(value)
    }
}
