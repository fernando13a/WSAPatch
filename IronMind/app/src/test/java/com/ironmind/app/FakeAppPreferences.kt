package com.ironmind.app

import com.ironmind.app.data.preferences.AppPreferences

/** In-memory [AppPreferences] for ViewModel tests. */
class FakeAppPreferences(
    override var modelDownloadPrompted: Boolean = false,
) : AppPreferences
