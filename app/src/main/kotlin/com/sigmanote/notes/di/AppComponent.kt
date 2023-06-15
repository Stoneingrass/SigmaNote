

package com.sigmanote.notes.di

import android.content.Context
import com.sigmanote.notes.App
import com.sigmanote.notes.receiver.AlarmReceiver
import com.sigmanote.notes.ui.edit.EditFragment
import com.sigmanote.notes.ui.home.HomeFragment
import com.sigmanote.notes.ui.labels.LabelEditDialog
import com.sigmanote.notes.ui.labels.LabelFragment
import com.sigmanote.notes.ui.main.MainActivity
import com.sigmanote.notes.ui.notification.NotificationActivity
import com.sigmanote.notes.ui.reminder.ReminderDialog
import com.sigmanote.notes.ui.search.SearchFragment
import com.sigmanote.notes.ui.settings.ExportPasswordDialog
import com.sigmanote.notes.ui.settings.ImportPasswordDialog
import com.sigmanote.notes.ui.settings.SettingsFragment
import com.sigmanote.notes.ui.sort.SortDialog
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent {

    fun inject(app: App)

    fun inject(activity: MainActivity)
    fun inject(activity: NotificationActivity)

    fun inject(fragment: HomeFragment)
    fun inject(fragment: SearchFragment)
    fun inject(fragment: EditFragment)
    fun inject(fragment: LabelFragment)
    fun inject(fragment: SettingsFragment)
    fun inject(dialog: ReminderDialog)
    fun inject(dialog: LabelEditDialog)
    fun inject(dialog: SortDialog)
    fun inject(dialog: ExportPasswordDialog)
    fun inject(dialog: ImportPasswordDialog)
    fun inject(receiver: AlarmReceiver)

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance appContext: Context): AppComponent
    }
}
