

package com.sigmanote.notes.model.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Entity(tableName = "labels")
@Parcelize
data class Label(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    @Transient
    val id: Long = NO_ID,

    @ColumnInfo(name = "name", index = true)
    val name: String,

    @ColumnInfo(name = "hidden")
    val hidden: Boolean = false,
) : Parcelable {

    companion object {
        const val NO_ID = 0L
    }
}

@Entity(
    tableName = "label_refs",
    primaryKeys = ["noteId", "labelId"],
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Label::class,
            parentColumns = ["id"],
            childColumns = ["labelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ]
)
data class LabelRef(
    @ColumnInfo(name = "noteId", index = true)
    val noteId: Long,

    @ColumnInfo(name = "labelId", index = true)
    val labelId: Long,
)
