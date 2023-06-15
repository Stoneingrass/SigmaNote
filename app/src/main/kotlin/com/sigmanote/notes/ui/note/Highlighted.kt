

package com.sigmanote.notes.ui.note

data class Highlighted(
    val content: String,
    val highlights: List<IntRange> = emptyList()
)
