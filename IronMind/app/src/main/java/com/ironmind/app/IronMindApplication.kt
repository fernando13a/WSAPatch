package com.ironmind.app

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.ironmind.app.domain.usecase.AppInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Application entry point. [HiltAndroidApp] triggers Hilt's code generation and DI graph. */
@HiltAndroidApp
class IronMindApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var appInitializer: AppInitializer

    override fun onCreate() {
        super.onCreate()
        // Initialize app in background (populate Spanish names, etc.)
        CoroutineScope(Dispatchers.Default).launch {
            appInitializer.initialize()
        }
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
