

package com.sigmanote.notes.ui.note.adapter

import androidx.recyclerview.widget.DiffUtil

class NoteListDiffCallback : DiffUtil.ItemCallback<NoteListItem>() {

    override fun areItemsTheSame(old: NoteListItem, new: NoteListItem) = old.id == new.id

    override fun areContentsTheSame(old: NoteListItem, new: NoteListItem): Boolean {
        if (new.type != old.type) {
            return false
        }

        return when (new) {
            is MessageItem -> {
                old as MessageItem
                new.message == old.message
            }
            is HeaderItem -> {
                old as HeaderItem
                new.title == old.title
            }
            is NoteItem -> {
                old as NoteItem
                val oldNote = old.note
                val newNote = new.note
                new.checked == old.checked &&
                        newNote.type == oldNote.type &&
                        newNote.status == oldNote.status &&
                        newNote.pinned == oldNote.pinned &&
                        newNote.title == oldNote.title &&
                        newNote.content == oldNote.content &&
                        newNote.metadata == oldNote.metadata &&
                        newNote.reminder == oldNote.reminder &&
                        new.labels == old.labels &&
                        new.showMarkAsDone == old.showMarkAsDone &&
                        when (new) {
                            is NoteItemText -> {
                                old as NoteItemText
                                new.content == old.content
                            }
                            is NoteItemList -> {
                                old as NoteItemList
                                new.items == old.items
                            }
                        }
            }
        }
    }
}
