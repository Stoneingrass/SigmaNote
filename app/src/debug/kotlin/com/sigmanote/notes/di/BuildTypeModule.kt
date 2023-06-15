

package com.sigmanote.notes.di

import com.sigmanote.notes.ui.home.BuildTypeBehavior
import com.sigmanote.notes.ui.home.DebugBuildTypeBehavior
import dagger.Binds
import dagger.Module

@Module
abstract class BuildTypeModule {

    @get:Binds
    abstract val DebugBuildTypeBehavior.bind: BuildTypeBehavior
}
