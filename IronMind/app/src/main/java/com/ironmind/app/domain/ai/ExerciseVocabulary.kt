package com.ironmind.app.domain.ai

/**
 * Composicional vocabulary for translating exercise names from free-exercise-db to Spanish.
 * Maps English terms (barbell, dumbbell, machine, etc.) to Spanish equivalents,
 * enabling efficient translation of compound exercise names.
 *
 * For example: "Dumbbell Bench Press" → "Press de Mancuerna en Banco"
 */
object ExerciseVocabulary {

    // Equipment names
    private val equipment = mapOf(
        "barbell" to "Barra",
        "dumbbell" to "Mancuerna",
        "cable" to "Poleas",
        "machine" to "Máquina",
        "kettlebell" to "Pesa Rusa",
        "medicine ball" to "Balón Medicinal",
        "resistance band" to "Banda Elástica",
        "trap bar" to "Trampa",
        "smith machine" to "Máquina Smith",
        "leg press" to "Prensa de Piernas",
        "hack squat" to "Sentadilla Hack",
        "plate-loaded" to "Cargada con Placas",
        "leverage" to "Palanca",
        "sled" to "Trineo",
        "peg board" to "Tablero de Clavijas",
    )

    // Muscle group names (already in MuscleGroup enum in Spanish)
    private val muscles = mapOf(
        "chest" to "Pecho",
        "back" to "Espalda",
        "shoulders" to "Hombros",
        "biceps" to "Bíceps",
        "triceps" to "Tríceps",
        "forearms" to "Antebrazos",
        "quadriceps" to "Cuádriceps",
        "hamstrings" to "Isquiotibiales",
        "glutes" to "Glúteos",
        "calves" to "Pantorrillas",
        "abs" to "Abdominales",
        "obliques" to "Oblicuos",
    )

    // Common exercise movement terms
    private val movements = mapOf(
        "press" to "Press",
        "pull" to "Jalón",
        "curl" to "Curl",
        "extension" to "Extensión",
        "raise" to "Elevación",
        "fly" to "Vuelo",
        "row" to "Remada",
        "squat" to "Sentadilla",
        "deadlift" to "Peso Muerto",
        "lunge" to "Estocada",
        "step" to "Paso",
        "walk" to "Caminata",
        "carry" to "Carga",
        "shrug" to "Encogimiento",
        "twist" to "Giro",
        "crunch" to "Crunch",
        "plank" to "Plancha",
        "hold" to "Sostén",
        "dip" to "Fondo",
        "pushup" to "Flexión",
        "pushdown" to "Presión Hacia Abajo",
        "pulldown" to "Jalón Hacia Abajo",
        "lateral raise" to "Elevación Lateral",
        "front raise" to "Elevación Frontal",
        "overhead press" to "Press Aéreo",
        "bent over" to "Inclinado",
        "upright" to "Vertical",
        "close grip" to "Agarre Cerrado",
        "wide grip" to "Agarre Amplio",
        "sumo" to "Sumo",
        "trap" to "Trampa",
        "sled" to "Trineo",
        "machine" to "Máquina",
        "smith" to "Smith",
        "incline" to "Inclinado",
        "decline" to "Declinado",
        "flat" to "Plano",
        "assisted" to "Asistido",
        "weighted" to "Ponderado",
        "bodyweight" to "Peso Corporal",
        "single" to "Individual",
        "double" to "Doble",
        "alternating" to "Alternado",
    )

    // Full exercise name overrides (for ambiguous or compound names)
    private val fullNameOverrides = mapOf(
        // "bench" has no entry in equipment/movements/muscles, and even if it did, word-by-word
        // composition preserves English word order ("Barra Banco Press"), not the natural
        // Spanish "Press de Banco" — one of the most common lifts in the catalog, so it gets an
        // explicit override rather than falling through to an untranslated/awkward composition.
        "barbell bench press" to "Barra Press de Banco",
        "ab wheel" to "Rueda Abdominal",
        "sled push" to "Empuje de Trineo",
        "sled pull" to "Jalón de Trineo",
        "cable crossover" to "Cruce en Polea",
        "cable fly" to "Vuelo en Polea",
        "cable curl" to "Curl en Polea",
        "cable press" to "Press en Polea",
        "cable row" to "Remada en Polea",
        "cable woodchop" to "Giro de Leña en Polea",
        "cable kickback" to "Patada hacia Atrás en Polea",
        "cable pull through" to "Tracción a través en Polea",
        "machine curl" to "Curl en Máquina",
        "machine press" to "Press en Máquina",
        "machine row" to "Remada en Máquina",
        "machine fly" to "Vuelo en Máquina",
        "leg press" to "Prensa de Piernas",
        "leg curl" to "Curl de Piernas",
        "leg extension" to "Extensión de Piernas",
        "leg raise" to "Elevación de Piernas",
        "smith squat" to "Sentadilla en Smith",
        "hack squat" to "Sentadilla Hack",
        "belt squat" to "Sentadilla con Cinturón",
        "plate loaded squat" to "Sentadilla Cargada con Placas",
        "plate loaded chest press" to "Press de Pecho Cargado con Placas",
        "plate loaded leg press" to "Prensa de Piernas Cargada con Placas",
        "plate loaded row" to "Remada Cargada con Placas",
        "landmine press" to "Press en Mina",
        "landmine row" to "Remada en Mina",
        "landmine squat" to "Sentadilla en Mina",
        "battle ropes" to "Cuerdas de Batalla",
        "jump rope" to "Salto de Cuerda",
        "resistance band" to "Banda Elástica",
        "exercise ball" to "Balón de Estabilidad",
        "medicine ball" to "Balón Medicinal",
        "kettlebell swing" to "Balanceo de Pesa Rusa",
        "farmer walk" to "Caminata del Granjero",
        "bear crawl" to "Gateo de Oso",
        "mountain climber" to "Escalada de Montaña",
        "wall sit" to "Sentada en Pared",
        "plank" to "Plancha",
        "handstand" to "Postura de Manos",
        "pistol squat" to "Sentadilla Pistola",
        "nordic hamstring" to "Isquiotibial Nórdico",
        "sissy squat" to "Sentadilla Sissy",
        "belt squat" to "Sentadilla con Cinturón",
        "stair climber" to "Escaladora",
        "rowing machine" to "Máquina de Remo",
        "treadmill" to "Cinta Rodante",
        "elliptical" to "Elíptica",
        "stationary bike" to "Bicicleta Estática",
        "spin bike" to "Bicicleta de Spinning",
        "cable machine" to "Máquina de Poleas",
        "smith machine" to "Máquina Smith",
        "leverage machine" to "Máquina de Palanca",
        "plate loaded machine" to "Máquina Cargada con Placas",
    )

    /**
     * Translate an exercise name from English to Spanish using vocabulary composition.
     * Returns null if translation cannot be composed from vocabulary.
     */
    fun translateName(englishName: String): String? {
        val lowerName = englishName.lowercase()

        // Check full name overrides first
        fullNameOverrides.forEach { (key, value) ->
            if (lowerName == key) return value
        }

        // Try to compose from parts
        val words = lowerName.split(" ")
        val translatedParts = mutableListOf<String>()

        for (word in words) {
            val translated = equipment[word]
                ?: movements[word]
                ?: muscles[word]
                ?: word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            translatedParts.add(translated)
        }

        // Only return composed translation if at least one word was translated from vocabulary
        val hasVocabularyMatch = translatedParts.zip(words).any { (translated, original) ->
            equipment.containsKey(original) || movements.containsKey(original) || muscles.containsKey(original)
        }

        return if (hasVocabularyMatch) translatedParts.joinToString(" ") else null
    }
}
