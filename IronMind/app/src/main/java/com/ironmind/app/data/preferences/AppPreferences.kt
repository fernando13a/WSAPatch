package com.ironmind.app.data.preferences

/**
 * Lightweight, per-device UI flags (not user data). An interface so ViewModels stay free of
 * Android's SharedPreferences and can be unit-tested with a fake.
 */
interface AppPreferences {

    /** Whether the first-launch "download the AI model" prompt has already been shown. */
    var modelDownloadPrompted: Boolean
}
