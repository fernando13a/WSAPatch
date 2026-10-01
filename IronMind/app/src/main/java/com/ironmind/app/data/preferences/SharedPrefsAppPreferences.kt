package com.ironmind.app.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.ironmind.app.domain.model.WeightUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _weightUnit = MutableStateFlow(loadWeightUnit())
    override val weightUnitFlow: StateFlow<WeightUnit> = _weightUnit.asStateFlow()

    override var weightUnit: WeightUnit
        get() = _weightUnit.value
        set(value) {
            prefs.edit { putString(KEY_WEIGHT_UNIT, value.name) }
            _weightUnit.value = value
        }

    private fun loadWeightUnit(): WeightUnit =
        runCatching { WeightUnit.valueOf(prefs.getString(KEY_WEIGHT_UNIT, WeightUnit.KG.name)!!) }
            .getOrDefault(WeightUnit.KG)

    private companion object {
        const val KEY_MODEL_PROMPTED = "model_download_prompted"
        const val KEY_WEIGHT_UNIT = "weight_unit"
    }
}
