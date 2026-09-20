package com.ironmind.app.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseVocabularyTest {

    @Test
    fun translatesSimpleEquipmentNames() {
        val translated = ExerciseVocabulary.translateName("Dumbbell Press")
        assertNotNull(translated)
        assertEquals("Mancuerna Press", translated)
    }

    @Test
    fun translatesComplexExerciseNames() {
        val translated = ExerciseVocabulary.translateName("Barbell Bench Press")
        assertNotNull(translated)
        assertEquals("Barra Press de Banco", translated)
    }

    @Test
    fun usesFullNameOverridesWhenAvailable() {
        val translated = ExerciseVocabulary.translateName("Plate Loaded Chest Press")
        assertNotNull(translated)
        assertEquals("Press de Pecho Cargado con Placas", translated)
    }

    @Test
    fun returnsNullForUnknownNames() {
        val translated = ExerciseVocabulary.translateName("xyz unknown exercise 123")
        assertNull(translated)
    }

    @Test
    fun capitalizesProperlyInComposedTranslations() {
        val translated = ExerciseVocabulary.translateName("kettlebell squat")
        assertNotNull(translated)
        assertEquals("Pesa Rusa Sentadilla", translated)
    }

    @Test
    fun translatesEquipmentVariants() {
        val translated = ExerciseVocabulary.translateName("Cable Curl")
        assertNotNull(translated)
        assertEquals("Poleas Curl", translated)
    }
}
