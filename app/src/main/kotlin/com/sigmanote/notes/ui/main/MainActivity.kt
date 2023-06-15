
package com.sigmanote.notes.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.contains
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.color.DynamicColors
import com.sigmanote.notes.App
import com.sigmanote.notes.NavGraphMainDirections
import com.sigmanote.notes.R
import com.sigmanote.notes.TAG
import com.sigmanote.notes.databinding.ActivityMainBinding
import com.sigmanote.notes.model.PrefsManager
import com.sigmanote.notes.model.converter.NoteTypeConverter
import com.sigmanote.notes.model.entity.Note
import com.sigmanote.notes.navigateSafe
import com.sigmanote.notes.receiver.AlarmReceiver
import com.sigmanote.notes.ui.navGraphViewModel
import com.sigmanote.notes.ui.navigation.HomeDestination
import com.sigmanote.notes.ui.observeEvent
import com.sigmanote.notes.ui.viewModel
import javax.inject.Inject
import javax.inject.Provider

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {

    @Inject
    lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    private val sharedViewModel by navGraphViewModel(R.id.nav_graph_main) { sharedViewModelProvider.get() }

    @Inject
    lateinit var viewModelFactory: MainViewModel.Factory
    private val viewModel by viewModel { viewModelFactory.create(it) }

    @Inject
    lateinit var prefs: PrefsManager

    lateinit var drawerLayout: DrawerLayout

    private lateinit var navController: NavController
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_DayNight)

        super.onCreate(savedInstanceState)
        (applicationContext as App).appComponent.inject(this)

        if (prefs.dynamicColors) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        drawerLayout = binding.drawerLayout
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        navController.addOnDestinationChangedListener(this)

        binding.navView.setNavigationItemSelectedListener { item ->
            viewModel.navigationItemSelected(
                item,
                binding.navView.menu.findItem(R.id.drawer_labels).subMenu!!
            )
            true
        }
        viewModel.startPopulatingDrawerWithLabels()

        onBackPressedDispatcher.addCallback(this) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawers()
            } else {

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }

        setupViewModelObservers()
    }

    private fun setupViewModelObservers() {
        val menu = binding.navView.menu
        val labelSubmenu = menu.findItem(R.id.drawer_labels).subMenu!!
        var currentHomeDestination: HomeDestination = HomeDestination.Status(NoteStatus.ACTIVE)

        viewModel.currentHomeDestination.observe(this) { newHomeDestination ->
            sharedViewModel.changeHomeDestination(newHomeDestination)
            currentHomeDestination = newHomeDestination
        }

        viewModel.navDirectionsEvent.observeEvent(this) { navDirections ->
            navController.navigateSafe(navDirections)
        }

        viewModel.drawerCloseEvent.observeEvent(this) {
            drawerLayout.closeDrawers()
        }

        viewModel.clearLabelsEvent.observeEvent(this) {
            labelSubmenu.clear()
        }

        viewModel.labelsAddEvent.observeEvent(this) { labels ->
            if (labels != null) {
                for (label in labels) {
                    labelSubmenu.add(Menu.NONE, View.generateViewId(), Menu.NONE, label.name)
                        .setIcon(R.drawable.ic_label_outline).isCheckable = true
                }
            }


            if (currentHomeDestination is HomeDestination.Labels) {
                val currentLabelName = (currentHomeDestination as HomeDestination.Labels).label.name
                if (binding.navView.checkedItem != null && (
                            binding.navView.checkedItem!! !in labelSubmenu ||
                                    binding.navView.checkedItem!!.title != currentLabelName)
                    || binding.navView.checkedItem == null
                ) {
                    labelSubmenu.forEach { item: MenuItem ->
                        if (item.title == currentLabelName) {
                            binding.navView.setCheckedItem(item)
                            return@forEach
                        }
                    }
                }
            }
        }

        viewModel.manageLabelsVisibility.observe(this) { isVisible ->
            menu.findItem(R.id.drawer_item_edit_labels).isVisible = isVisible
        }

        viewModel.editItemEvent.observeEvent(this) { noteId ->

            navController.navigateSafe(NavGraphMainDirections.actionEditNote(noteId), true)
        }

        viewModel.autoExportEvent.observeEvent(this) { uri ->
            viewModel.autoExport(try {

                contentResolver.openOutputStream(Uri.parse(uri), "wt")
            } catch (e: Exception) {
                Log.i(TAG, "Auto data export failed", e)
                null
            })
        }

        viewModel.createNoteEvent.observeEvent(this) { newNoteData ->
            navController.navigateSafe(NavGraphMainDirections.actionEditNote(
                type = newNoteData.type.value,
                title = newNoteData.title,
                content = newNoteData.content
            ))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
    }

    override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
        drawerLayout.setDrawerLockMode(if (destination.id == R.id.fragment_home) {
            DrawerLayout.LOCK_MODE_UNLOCKED
        } else {
            DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        })
    }

    override fun onStart() {
        super.onStart()


        sharedViewModel.labelAddEventNav.observeEvent(this) { label ->
            if (navController.previousBackStackEntry?.destination?.id == R.id.fragment_home) {
                viewModel.selectLabel(label)
            }
        }

        viewModel.onStart()
    }

    override fun onResume() {
        super.onResume()
        handleIntent()
    }

    override fun onDestroy() {
        super.onDestroy()
        navController.removeOnDestinationChangedListener(this)
    }

    private fun handleIntent() {
        val intent = intent ?: return
        if (!intent.getBooleanExtra(KEY_INTENT_HANDLED, false)) {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    // Plain text was shared to app, create new note for it
                    if (intent.type == "text/plain") {
                        val title = intent.getStringExtra(Intent.EXTRA_TITLE)
                            ?: intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
                        val content = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                        viewModel.createNote(NoteType.TEXT, title, content)
                    }
                }
                INTENT_ACTION_CREATE -> {

                    val type = NoteTypeConverter.toType(
                        intent.getIntExtra(EXTRA_NOTE_TYPE, 0))
                    viewModel.createNote(type)
                }
                INTENT_ACTION_EDIT -> {

                    viewModel.editNote(intent.getLongExtra(AlarmReceiver.EXTRA_NOTE_ID, Note.NO_ID))
                }
                INTENT_ACTION_SHOW_REMINDERS -> {

                    binding.navView.menu.findItem(R.id.drawer_item_reminders).isChecked = true
                    sharedViewModel.changeHomeDestination(HomeDestination.Reminders)
                }
            }


            intent.putExtra(KEY_INTENT_HANDLED, true)
        }
    }

    companion object {
        private const val KEY_INTENT_HANDLED = "com.sigmanote.notes.INTENT_HANDLED"

        const val EXTRA_NOTE_TYPE = "com.sigmanote.notes.NOTE_TYPE"

        const val INTENT_ACTION_CREATE = "com.sigmanote.notes.CREATE"
        const val INTENT_ACTION_EDIT = "com.sigmanote.notes.EDIT"
        const val INTENT_ACTION_SHOW_REMINDERS = "com.sigmanote.notes.SHOW_REMINDERS"
    }
}
