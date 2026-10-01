package com.ironmind.app.data.local.converter

import androidx.room.TypeConverter
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit

/**
 * Room type converters for the domain enums. Enums are stored by [Enum.name] (a stable,
 * human-readable string) rather than ordinal, so reordering an enum never corrupts data.
 */
class Converters {

    @TypeConverter
    fun fromMuscleGroup(value: MuscleGroup): String = value.name

    @TypeConverter
    fun toMuscleGroup(value: String): MuscleGroup = enumValueOrDefault(value, MuscleGroup.OTHER)

    @TypeConverter
    fun fromEquipment(value: Equipment): String = value.name

    @TypeConverter
    fun toEquipment(value: String): Equipment = enumValueOrDefault(value, Equipment.OTHER)

    @TypeConverter
    fun fromRoutineSplit(value: RoutineSplit): String = value.name

    @TypeConverter
    fun toRoutineSplit(value: String): RoutineSplit = enumValueOrDefault(value, RoutineSplit.CUSTOM)

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
