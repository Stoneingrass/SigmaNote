
package com.sigmanote.notes.screenshot

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.sigmanote.notes.App
import com.sigmanote.notes.R
import com.sigmanote.notes.hideKeyboard
import com.sigmanote.notes.model.PrefsManager
import com.sigmanote.notes.model.entity.LabelRef
import com.sigmanote.notes.model.entity.Reminder
import com.sigmanote.notes.ui.edit.adapter.EditItemViewHolder
import com.sigmanote.notes.ui.main.MainActivity
import com.sigmanote.notes.ui.note.ShownDateField
import com.sigmanote.notes.ui.note.adapter.NoteViewHolder
import com.sigmanote.notesshared.dateFor
import com.sigmanote.recurpicker.Recurrence
import com.sigmanote.recurpicker.RecurrenceFinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.endsWith
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import com.sigmanote.notes.test.R as RT


@RunWith(AndroidJUnit4::class)
@LargeTest
@Ignore("not a test, comment this annotation to take screenshots")
class Screenshots {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    val screenshotTestRule = ScreenshotTestRule()

    private lateinit var context: Context

    private lateinit var notesDao: NotesDao
    private lateinit var labelsDao: LabelsDao

    private lateinit var prefs: PrefsManager

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<App>()
        context = app

        notesDao = app.database.notesDao()
        labelsDao = app.database.labelsDao()
        prefs = app.prefs
        runBlocking(Dispatchers.Main) {
            // main thread needed for clearing prefs
            notesDao.clear()
            labelsDao.clear()
            prefs.clear(context)
        }
    }



    @Test
    fun screenshotEdit() = screenshotTest {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(PrefsManager.SHOWN_DATE, ShownDateField.MODIFIED.value).apply()
        notesDao.insertAll(listOf(
            ScreenshotHelper.getNote(1).copy(addedDate = dateFor("2020-01-01"),
                lastModifiedDate = dateFor("2021-03-23T17:34:00.000Z")
            ),
        ))
        labelsDao.insert(ScreenshotHelper.getLabel(1))
        labelsDao.insertRefs(listOf(
            LabelRef(1, 1),
        ))

        onView(allOf(isDescendantOfA(withId(R.id.fragment_note_layout)), withId(R.id.recycler_view)))
            .perform(actionOnItemAtPosition<NoteViewHolder<*>>(0, click()))
        delay(250)
        onView(allOf(isDescendantOfA(withId(R.id.fragment_edit_layout)), withId(R.id.recycler_view)))
            .perform(actionOnItemAtPosition<EditItemViewHolder>(3, click()))
        delay(500)

        ScreenshotHelper.takeScreenshot("1")
    }


    @Test
    fun screenshotHome() = screenshotTest {
        notesDao.insertAll(listOf(
            ScreenshotHelper.getNote(1).copy(pinned = PinnedStatus.PINNED),
            ScreenshotHelper.getNote(2).copy(pinned = PinnedStatus.PINNED),
            ScreenshotHelper.getNote(3),
            ScreenshotHelper.getNote(4),
            ScreenshotHelper.getNote(5),
            ScreenshotHelper.getNote(6),
        ))
        labelsDao.insert(ScreenshotHelper.getLabel(1))
        labelsDao.insert(ScreenshotHelper.getLabel(2))
        labelsDao.insertRefs(listOf(
            LabelRef(1, 1),
            LabelRef(2, 1),
            LabelRef(3, 2),
            LabelRef(5, 2),
        ))

        onView(toolbarItem(R.id.item_layout)).perform(click())

        ScreenshotHelper.takeScreenshot("2")
    }


    @Test
    fun screenshotReminder() = screenshotTest {
        notesDao.insertAll(listOf(
            ScreenshotHelper.getNote(1).copy(reminder = dummyReminder, pinned = PinnedStatus.PINNED),
            ScreenshotHelper.getNote(2),
            ScreenshotHelper.getNote(3),
            ScreenshotHelper.getNote(5),
        ))
        labelsDao.insert(ScreenshotHelper.getLabel(1))
        labelsDao.insert(ScreenshotHelper.getLabel(2))
        labelsDao.insertRefs(listOf(
            LabelRef(2, 1),
            LabelRef(5, 2),
        ))

        onView(allOf(isDescendantOfA(withId(R.id.fragment_note_layout)), withId(R.id.recycler_view)))
            .perform(actionOnItemAtPosition<NoteViewHolder<*>>(1, longClick()))
        delay(250)
        onView(toolbarItem(R.id.item_reminder)).perform(click())

        ScreenshotHelper.takeScreenshot("3")
    }

    @Test
    fun screenshotSearch() = screenshotTest(true) {
        val queryText = InstrumentationRegistry.getInstrumentation()
            .context.getString(RT.string.screenshot_search_query)
        notesDao.insertAll(listOf(
            ScreenshotHelper.getNote(1).copy(reminder = dummyReminder),
            ScreenshotHelper.getNote(2).copy(status = NoteStatus.ARCHIVED, pinned = PinnedStatus.CANT_PIN),
        ))
        labelsDao.insert(ScreenshotHelper.getLabel(1))
        labelsDao.insert(ScreenshotHelper.getLabel(2))
        labelsDao.insertRefs(listOf(
            LabelRef(2, 1),
        ))

        onView(toolbarItem(R.id.item_search)).perform(click())
        onView(withId(androidx.appcompat.R.id.search_src_text)).perform(typeText(queryText))
        activityRule.scenario.onActivity {
            it.findViewById<View>(androidx.appcompat.R.id.search_src_text).hideKeyboard()
        }
        delay(500)

        ScreenshotHelper.takeScreenshot("4")
    }


    @Test
    fun screenshotNavigation() = screenshotTest(true) {
        notesDao.insertAll(listOf(
            ScreenshotHelper.getNote(1),
            ScreenshotHelper.getNote(2),
            ScreenshotHelper.getNote(5),
        ))
        labelsDao.insert(ScreenshotHelper.getLabel(1))
        labelsDao.insert(ScreenshotHelper.getLabel(2))
        labelsDao.insert(ScreenshotHelper.getLabel(3))
        labelsDao.insertRefs(listOf(
            LabelRef(1, 1),
            LabelRef(2, 1),
            LabelRef(5, 2),
        ))

        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        delay(250)

        ScreenshotHelper.takeScreenshot("5")
    }

    private val dummyReminder: Reminder
        get() {
            val reminderTime = Calendar.getInstance().apply {
                add(Calendar.DATE, 1)
                this[Calendar.HOUR_OF_DAY] = 8
                this[Calendar.MINUTE] = 0
            }.time
            return Reminder.create(reminderTime, Recurrence(Recurrence.Period.DAILY), RecurrenceFinder())
        }

    private fun toolbarItem(id: Int): Matcher<View> {
        return allOf(withClassName(endsWith("ItemView")), withId(id))
    }

    private inline fun screenshotTest(
        darkMode: Boolean = false,
        crossinline block: suspend CoroutineScope.() -> Unit
    ) {
        runBlocking {
            withContext(Dispatchers.Main) {
                AppCompatDelegate.setDefaultNightMode(if (darkMode) {
                    AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                })
            }
            screenshotTestRule.startStatusBarDemo()
            block()
            screenshotTestRule.stopStatusBarDemo()
        }
    }
}
