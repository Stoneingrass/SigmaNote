

package com.sigmanote.notes.model.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class NoteMetadata

@Serializable
@SerialName("blank")
object BlankNoteMetadata : NoteMetadata() {
    override fun toString() = "none"
}

@Serializable
@SerialName("list")
data class ListNoteMetadata(
    @SerialName("checked")
    val checked: List<Boolean>
) : NoteMetadata()
