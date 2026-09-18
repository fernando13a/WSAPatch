package com.ironmind.app.data.preferences

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** SharedPreferences-backed [AppPreferences]. */
@Singleton
class SharedPrefsAppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : AppPreferences {

    private val prefs = context.getSharedPreferences("ironmind_prefs", Context.MODE_PRIVATE)

    override var modelDownloadPrompted: Boolean
        get() = prefs.getBoolean(KEY_MODEL_PROMPTED, false)
        set(value) = prefs.edit { putBoolean(KEY_MODEL_PROMPTED, value) }

    private companion object {
        const val KEY_MODEL_PROMPTED = "model_download_prompted"
    }
}
