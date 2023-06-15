
package com.sigmanote.notes.model

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.sigmanote.notes.OpenForTesting
import com.sigmanote.notes.R
import com.sigmanote.notes.ui.AppTheme
import com.sigmanote.notes.ui.note.ShownDateField
import com.sigmanote.notes.ui.note.SwipeAction
import com.sigmanote.notes.ui.note.adapter.NoteListLayoutMode
import org.jetbrains.annotations.TestOnly
import javax.inject.Inject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration.Companion.days

@OpenForTesting
class PrefsManager @Inject constructor(
    private val prefs: SharedPreferences,
) {

    val theme: AppTheme by enumPreference(THEME, AppTheme.SYSTEM)
    val dynamicColors: Boolean by preference(DYNAMIC_COLORS, true)
    val strikethroughChecked: Boolean by preference(STRIKETHROUGH_CHECKED, false)
    val moveCheckedToBottom: Boolean by preference(MOVE_CHECKED_TO_BOTTOM, false)
    var listLayoutMode: NoteListLayoutMode by enumPreference(LIST_LAYOUT_MODE, NoteListLayoutMode.LIST)
    val swipeActionLeft: SwipeAction by enumPreference(SWIPE_ACTION_LEFT, SwipeAction.ARCHIVE)
    val swipeActionRight: SwipeAction by enumPreference(SWIPE_ACTION_RIGHT, SwipeAction.ARCHIVE)
    val shownDateField: ShownDateField by enumPreference(SHOWN_DATE, ShownDateField.NONE)
    val maximumPreviewLabels: Int by preference(PREVIEW_LABELS, 0)

    var sortField: SortField by enumPreference(SORT_FIELD, SortField.MODIFIED_DATE)
    var sortDirection: SortDirection by enumPreference(SORT_DIRECTION, SortDirection.DESCENDING)

    var shouldEncryptExportedData: Boolean by preference(ENCRYPTED_EXPORT, false)
    var encryptedExportKeyDerivationSalt: String by preference(ENCRYPTED_EXPORT_KEY_DERIVATION_SALT, "")
    var encryptedImportKeyDerivationSalt: String by preference(ENCRYPTED_IMPORT_KEY_DERIVATION_SALT, "")
    var shouldAutoExport: Boolean by preference(AUTO_EXPORT, false)
    var autoExportUri: String by preference(AUTO_EXPORT_URI, "")
    var autoExportFailed: Boolean by preference(AUTO_EXPORT_FAILED, false)
    var lastAutoExportTime: Long by preference(LAST_AUTO_EXPORT_TIME, 0)

    var lastTrashReminderTime: Long by preference(LAST_TRASH_REMIND_TIME, 0)
    var lastRestrictedBatteryReminderTime: Long by preference(LAST_RESTRICTED_BATTERY_REMIND_TIME, 0)

    val sortSettings: SortSettings
        get() = SortSettings(sortField, sortDirection)

    fun getMaximumPreviewLines(noteType: NoteType): Int {
        val key = when (listLayoutMode) {
            NoteListLayoutMode.LIST -> when (noteType) {
                NoteType.TEXT -> PREVIEW_LINES_TEXT_LIST
                NoteType.LIST -> PREVIEW_LINES_LIST_LIST
            }
            NoteListLayoutMode.GRID -> when (noteType) {
                NoteType.TEXT -> PREVIEW_LINES_TEXT_GRID
                NoteType.LIST -> PREVIEW_LINES_LIST_GRID
            }
        }
        return prefs.getInt(key, 0)
    }

    fun setDefaults(context: Context) {
        for (prefsRes in PREFS_XML) {
            PreferenceManager.setDefaultValues(context, prefsRes, true)
        }
    }

    fun disableAutoExport() {
        shouldAutoExport = false
        lastAutoExportTime = 0
        autoExportFailed = false
        autoExportUri = AUTO_EXPORT_NO_URI
    }

    @TestOnly
    fun clear(context: Context) {
        for (prefsRes in PREFS_XML) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().clear().apply()
        }
        setDefaults(context)
    }

    private fun <T> preference(key: String, default: T) =
        object : ReadWriteProperty<PrefsManager, T> {
            @Suppress("UNCHECKED_CAST")
            override fun getValue(thisRef: PrefsManager, property: KProperty<*>) =
                thisRef.prefs.all.getOrElse(key) { default } as T

            override fun setValue(thisRef: PrefsManager, property: KProperty<*>, value: T) {
                thisRef.prefs.edit {
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is String -> putString(key, value)
                        else -> error("Unsupported preference type")
                    }
                }
            }
        }

    private inline fun <reified T> enumPreference(key: String, default: T) where T : ValueEnum<*>, T : Enum<T> =
        object : ReadWriteProperty<PrefsManager, T> {
            override fun getValue(thisRef: PrefsManager, property: KProperty<*>): T {
                val value = thisRef.prefs.all.getOrElse(key) { default.value }
                return enumValues<T>().first { it.value == value }
            }

            override fun setValue(thisRef: PrefsManager, property: KProperty<*>, value: T) {
                prefs.edit {
                    when (val v = value.value) {
                        is Int -> putInt(key, v)
                        is String -> putString(key, v)
                        else -> error("Unsupported enum preference value type")
                    }
                }
            }
        }

    fun migratePreferences() {
        val editorDelegate = lazy { prefs.edit() }
        val editor by editorDelegate

        // v1.4.2 -> v1.5.0
        val swipeAction = prefs.getString(SWIPE_ACTION, null)
        if (swipeAction != null) {
            // split value into two keys, one per direction
            editor.remove(SWIPE_ACTION)
                .putString(SWIPE_ACTION_LEFT, swipeAction)
                .putString(SWIPE_ACTION_RIGHT, swipeAction)
        }

        if (editorDelegate.isInitialized()) {
            editor.apply()
        }
    }

    companion object {
        // Settings keys
        const val THEME = "theme"
        const val DYNAMIC_COLORS = "dynamic_colors"
        const val PREVIEW_LABELS = "preview_labels"
        const val PREVIEW_LINES = "preview_lines"
        const val PREVIEW_LINES_TEXT_LIST = "preview_lines_text_list"
        const val PREVIEW_LINES_LIST_LIST = "preview_lines_list_list"
        const val PREVIEW_LINES_TEXT_GRID = "preview_lines_text_grid"
        const val PREVIEW_LINES_LIST_GRID = "preview_lines_list_grid"
        const val STRIKETHROUGH_CHECKED = "strikethrough_checked"
        const val MOVE_CHECKED_TO_BOTTOM = "move_checked_to_bottom"
        const val SHOWN_DATE = "shown_date"
        const val SWIPE_ACTION_LEFT = "swipe_action_left"
        const val SWIPE_ACTION_RIGHT = "swipe_action_right"
        const val ENCRYPTED_EXPORT = "encrypted_export"
        const val EXPORT_DATA = "export_data"
        const val AUTO_EXPORT = "auto_export"
        const val IMPORT_DATA = "import_data"
        const val CLEAR_DATA = "clear_data"
        const val VIEW_LICENSES = "view_licenses"
        const val VERSION = "version"

        // Other keys
        private const val AUTO_EXPORT_URI = "auto_export_uri"
        private const val LIST_LAYOUT_MODE = "is_in_list_layout"
        private const val SORT_FIELD = "sort_field"
        private const val SORT_DIRECTION = "sort_direction"
        private const val LAST_TRASH_REMIND_TIME = "last_deleted_remind_time"
        private const val LAST_RESTRICTED_BATTERY_REMIND_TIME = "last_restricted_battery_remind_time"
        private const val LAST_AUTO_EXPORT_TIME = "last_auto_export_time"
        private const val AUTO_EXPORT_FAILED = "auto_export_failed"
        private const val ENCRYPTED_EXPORT_KEY_DERIVATION_SALT = "encrypted_export_key_derivation_salt"
        private const val ENCRYPTED_IMPORT_KEY_DERIVATION_SALT = "encrypted_import_key_derivation_salt"

        // Legacy keys
        private const val SWIPE_ACTION = "swipe_action"

        private val PREFS_XML = listOf(
            R.xml.prefs,
            R.xml.prefs_preview_lines,
        )

        val TRASH_AUTO_DELETE_DELAY = 7.days

        val TRASH_REMINDER_DELAY = 60.days

        val RESTRICTED_BATTERY_REMINDER_DELAY = 60.days

        val AUTO_EXPORT_DELAY = 1.days

        const val AUTO_EXPORT_NO_URI = ""

        const val MAXIMUM_RELATIVE_DATE_DAYS = 6
    }
}

