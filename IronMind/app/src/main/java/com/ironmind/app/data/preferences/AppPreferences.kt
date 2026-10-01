package com.ironmind.app.data.preferences

import com.ironmind.app.domain.model.WeightUnit
import kotlinx.coroutines.flow.StateFlow

/**
 * Lightweight, per-device UI flags & settings (not user data). An interface so ViewModels stay free
 * of Android's SharedPreferences and can be unit-tested with a fake.
 */
interface AppPreferences {

    /** Whether the first-launch "download the AI model" prompt has already been shown. */
    var modelDownloadPrompted: Boolean

    /** Preferred display/input unit for weights. Data is always stored in kg. */
    var weightUnit: WeightUnit

    /** Reactive view of [weightUnit] so screens re-render when it changes. */
    val weightUnitFlow: StateFlow<WeightUnit>
}
