package com.ironmind.app

import android.app.Application
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.ironmind.app.domain.usecase.AppInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Application entry point. [HiltAndroidApp] triggers Hilt's code generation and DI graph. */
@HiltAndroidApp
class IronMindApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var appInitializer: AppInitializer

    override fun onCreate() {
        super.onCreate()
        // Startup housekeeping (Spanish names, bundled images). A bare root launch would send any
        // throw here — Room opening, a migration, a disk error — to the default uncaught handler
        // and kill the process on the splash screen, leaving no way in to fix it from the Backup
        // screen. None of this work is worth the app for, so it's logged and dropped.
        CoroutineScope(SupervisorJob() + Dispatchers.Default + initializerErrorHandler).launch {
            appInitializer.initialize()
        }
    }

    private val initializerErrorHandler = CoroutineExceptionHandler { _, error ->
        Log.e("IronMind", "Startup initialization failed", error)
    }

    /**
     * The one [ImageLoader] every screen uses. Coil only shares the *disk* cache between
     * instances — memory cache, OkHttp client and the component/network callbacks each loader
     * registers are per-instance — so building one per composable would give exercise lists a
     * fresh loader (and a leaked callback) per row. Declaring it here lets Coil's `AsyncImage`
     * pick it up implicitly, with GIF/animated-WebP support so an attached GIF actually plays.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()
}
