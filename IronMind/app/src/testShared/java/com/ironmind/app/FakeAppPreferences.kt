package com.ironmind.app

import com.ironmind.app.data.preferences.AppPreferences
import com.ironmind.app.domain.model.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [AppPreferences] for ViewModel tests. */
class FakeAppPreferences(
    override var modelDownloadPrompted: Boolean = false,
    initialWeightUnit: WeightUnit = WeightUnit.KG,
) : AppPreferences {

    private val _weightUnit = MutableStateFlow(initialWeightUnit)
    override val weightUnitFlow: StateFlow<WeightUnit> = _weightUnit.asStateFlow()

    override var weightUnit: WeightUnit
        get() = _weightUnit.value
        set(value) { _weightUnit.value = value }
}
