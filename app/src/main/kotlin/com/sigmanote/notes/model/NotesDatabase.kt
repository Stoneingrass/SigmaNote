

package com.sigmanote.notes.model

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sigmanote.notes.model.converter.DateTimeConverter
import com.sigmanote.notes.model.converter.NoteMetadataConverter
import com.sigmanote.notes.model.converter.NoteStatusConverter
import com.sigmanote.notes.model.converter.NoteTypeConverter
import com.sigmanote.notes.model.converter.PinnedStatusConverter
import com.sigmanote.notes.model.converter.RecurrenceConverter
import com.sigmanote.notes.model.entity.Label
import com.sigmanote.notes.model.entity.LabelRef
import com.sigmanote.notes.model.entity.Note

@Database(
    entities = [
        Note::class,
        NoteFts::class,
        Label::class,
        LabelRef::class,
    ],
    version = NotesDatabase.VERSION)
@TypeConverters(
    DateTimeConverter::class,
    NoteTypeConverter::class,
    NoteStatusConverter::class,
    NoteMetadataConverter::class,
    PinnedStatusConverter::class,
    RecurrenceConverter::class,
)
abstract class NotesDatabase : RoomDatabase() {

    abstract fun notesDao(): NotesDao

    abstract fun labelsDao(): LabelsDao

    @Suppress("MagicNumber")
    companion object {
        const val VERSION = 4

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // By removing the sync feature, some data is now useless.
                // - Deleted notes table
                // - Synced flag on notes
                // - UUID flag on notes (unique ID across devices)
                database.apply {
                    execSQL("DROP TABLE deleted_notes")
                    execSQL("""CREATE TABLE notes_temp (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        type INTEGER NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, metadata TEXT NOT NULL, 
                        added_date INTEGER NOT NULL, modified_date INTEGER NOT NULL, status INTEGER NOT NULL)""")
                    execSQL("""INSERT INTO notes_temp (id, type, title, content, metadata, added_date, 
                        modified_date, status) SELECT id, type, title, content, metadata, added_date,
                        modified_date, status FROM notes""")
                    execSQL("DROP TABLE notes")
                    execSQL("ALTER TABLE notes_temp RENAME TO notes")
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Add pinned column to notes table. 'unpinned' for active notes, 'can't pin' for others.
                    execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                    execSQL("UPDATE notes SET pinned = 1 WHERE status == 0")

                    // Add reminder columns, all set to `null` by default.
                    execSQL("ALTER TABLE notes ADD COLUMN reminder_start INTEGER")
                    execSQL("ALTER TABLE notes ADD COLUMN reminder_recurrence TEXT")
                    execSQL("ALTER TABLE notes ADD COLUMN reminder_next INTEGER")
                    execSQL("ALTER TABLE notes ADD COLUMN reminder_count INTEGER")
                    execSQL("ALTER TABLE notes ADD COLUMN reminder_done INTEGER")

                    // Add label tables
                    execSQL("CREATE TABLE labels (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
                    execSQL("""CREATE TABLE label_refs (noteId INTEGER NOT NULL, labelId INTEGER NOT NULL,
                               PRIMARY KEY(noteId, labelId),
                               FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                               FOREIGN KEY(labelId) REFERENCES labels(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                    execSQL("CREATE INDEX index_labels_name ON labels (name)")
                    execSQL("CREATE INDEX IF NOT EXISTS index_label_refs_noteId ON label_refs (noteId)")
                    execSQL("CREATE INDEX IF NOT EXISTS index_label_refs_labelId ON label_refs (labelId)")
                }
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Add hidden attribute for labels
                    execSQL("ALTER TABLE labels ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
        )
    }
}
