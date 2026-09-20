package com.ironmind.app

import android.app.Application
import com.ironmind.app.domain.usecase.AppInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Application entry point. [HiltAndroidApp] triggers Hilt's code generation and DI graph. */
@HiltAndroidApp
class IronMindApplication : Application() {

    @Inject
    lateinit var appInitializer: AppInitializer

    override fun onCreate() {
        super.onCreate()
        // Initialize app in background (populate Spanish names, etc.)
        CoroutineScope(Dispatchers.Default).launch {
            appInitializer.initialize()
        }
    }
}
