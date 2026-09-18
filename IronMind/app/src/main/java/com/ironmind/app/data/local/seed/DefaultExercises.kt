package com.ironmind.app.data.local.seed

import com.ironmind.app.data.local.dao.WorkoutDao
import com.ironmind.app.data.local.entity.ExerciseEntity
import com.ironmind.app.data.local.entity.RoutineEntity
import com.ironmind.app.data.local.entity.RoutineExerciseCrossRef
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit

/**
 * Ships a starter catalog of common exercises and a few Push/Pull/Legs routines so the app is
 * useful on first launch while remaining 100% offline. Seeded rows are marked `isCustom = false`.
 */
object DefaultExercises {

    /** Inserts the starter catalog and routines if the exercises table is empty. */
    suspend fun seed(dao: WorkoutDao) {
        if (dao.countExercises() > 0) return
        dao.upsertExercises(catalog)
        seedRoutines(dao)
    }

    private suspend fun seedRoutines(dao: WorkoutDao) {
        val idByName = dao.getAllExercisesOnce().associate { it.name to it.id }

        suspend fun routine(name: String, split: RoutineSplit, exercises: List<String>) {
            val routineId = dao.upsertRoutine(RoutineEntity(name = name, split = split))
            exercises.forEachIndexed { index, exName ->
                idByName[exName]?.let { exId ->
                    dao.upsertRoutineExerciseCrossRef(
                        RoutineExerciseCrossRef(routineId = routineId, exerciseId = exId, position = index),
                    )
                }
            }
        }

        routine(
            "Push", RoutineSplit.PUSH,
            listOf("Barbell Bench Press", "Incline Dumbbell Press", "Overhead Press", "Lateral Raise", "Triceps Pushdown"),
        )
        routine(
            "Pull", RoutineSplit.PULL,
            listOf("Deadlift", "Pull-Up", "Bent-Over Barbell Row", "Lat Pulldown", "Barbell Curl"),
        )
        routine(
            "Legs", RoutineSplit.LEGS,
            listOf("Back Squat", "Leg Press", "Romanian Deadlift", "Leg Curl", "Standing Calf Raise"),
        )
    }

    private fun ex(
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment,
        instructions: String,
    ) = ExerciseEntity(
        name = name,
        muscleGroup = muscleGroup,
        equipment = equipment,
        isCustom = false,
        instructions = instructions,
    )

    private val catalog: List<ExerciseEntity> = listOf(
        // Push
        ex(
            "Barbell Bench Press", MuscleGroup.CHEST, Equipment.BARBELL,
            "1) Acuéstate en el banco con los ojos bajo la barra y los pies firmes en el suelo.\n" +
                "2) Agarra un poco más ancho que los hombros y saca la barra con las escápulas retraídas.\n" +
                "3) Baja controlado hasta la parte baja del pecho, codos a ~45°.\n" +
                "4) Empuja hasta extender los codos sin rebotar la barra en el pecho.",
        ),
        ex(
            "Incline Dumbbell Press", MuscleGroup.CHEST, Equipment.DUMBBELL,
            "1) Ajusta el banco a 30–45°. Sube las mancuernas a los hombros.\n" +
                "2) Parte con las mancuernas sobre el pecho alto, muñecas firmes.\n" +
                "3) Baja controlado hasta sentir estiramiento en el pecho.\n" +
                "4) Empuja juntando ligeramente arriba sin chocar las mancuernas.",
        ),
        ex(
            "Cable Fly", MuscleGroup.CHEST, Equipment.CABLE,
            "1) Poleas a la altura del pecho o algo más arriba. Un pie adelantado.\n" +
                "2) Codos ligeramente flexionados y fijos durante todo el movimiento.\n" +
                "3) Junta las manos al frente describiendo un arco, aprieta el pecho.\n" +
                "4) Regresa controlado hasta sentir estiramiento, sin dejar caer el peso.",
        ),
        ex(
            "Overhead Press", MuscleGroup.SHOULDERS, Equipment.BARBELL,
            "1) De pie, barra en los hombros, agarre a la anchura de los hombros.\n" +
                "2) Aprieta glúteos y core; codos ligeramente por delante de la barra.\n" +
                "3) Empuja la barra recta hacia arriba, moviendo la cabeza atrás lo justo.\n" +
                "4) Bloquea arriba con la barra sobre la mitad del pie; baja controlado.",
        ),
        ex(
            "Lateral Raise", MuscleGroup.SHOULDERS, Equipment.DUMBBELL,
            "1) De pie, mancuernas a los costados, leve flexión de codos.\n" +
                "2) Sube por los lados hasta la altura de los hombros, guiando con los codos.\n" +
                "3) Evita balanceo; no uses impulso de la cadera.\n" +
                "4) Baja lento (2–3 s). Peso moderado, técnica sobre carga.",
        ),
        ex(
            "Triceps Pushdown", MuscleGroup.TRICEPS, Equipment.CABLE,
            "1) Polea alta con barra o cuerda. Codos pegados al torso.\n" +
                "2) Extiende los codos hacia abajo hasta bloquear, sin mover los hombros.\n" +
                "3) Aprieta el tríceps abajo 1 segundo.\n" +
                "4) Sube controlado hasta ~90° sin dejar que la polea tire de más.",
        ),
        ex(
            "Overhead Triceps Extension", MuscleGroup.TRICEPS, Equipment.DUMBBELL,
            "1) Sujeta una mancuerna con ambas manos sobre la cabeza.\n" +
                "2) Codos apuntando al frente y pegados; core firme.\n" +
                "3) Baja la mancuerna por detrás de la cabeza flexionando solo los codos.\n" +
                "4) Extiende hasta arriba sin arquear la espalda.",
        ),
        // Pull
        ex(
            "Deadlift", MuscleGroup.BACK, Equipment.BARBELL,
            "1) Pies a la anchura de cadera, barra sobre la mitad del pie.\n" +
                "2) Bisagra de cadera, espalda neutra, pecho arriba, agarra la barra.\n" +
                "3) Empuja el suelo y sube la barra pegada a las piernas.\n" +
                "4) Bloquea cadera arriba; baja con control haciendo bisagra, sin redondear.",
        ),
        ex(
            "Pull-Up", MuscleGroup.BACK, Equipment.BODYWEIGHT,
            "1) Cuélgate con agarre un poco más ancho que los hombros, palmas al frente.\n" +
                "2) Baja los hombros y aprieta la espalda; sube llevando el pecho a la barra.\n" +
                "3) Barbilla por encima de la barra, sin balanceo.\n" +
                "4) Baja controlado hasta extender los brazos.",
        ),
        ex(
            "Bent-Over Barbell Row", MuscleGroup.BACK, Equipment.BARBELL,
            "1) Bisagra de cadera hasta el torso casi paralelo al suelo, espalda neutra.\n" +
                "2) Barra colgando, agarre prono a la anchura de los hombros.\n" +
                "3) Rema la barra hacia el abdomen bajo, codos cerca del cuerpo.\n" +
                "4) Aprieta la espalda arriba y baja controlado.",
        ),
        ex(
            "Lat Pulldown", MuscleGroup.BACK, Equipment.CABLE,
            "1) Sujeta la barra más ancho que los hombros, muslos fijos bajo el rodillo.\n" +
                "2) Pecho arriba, tira de la barra hacia la clavícula bajando los codos.\n" +
                "3) Aprieta los dorsales abajo; no te eches muy atrás.\n" +
                "4) Sube controlado hasta estirar por completo.",
        ),
        ex(
            "Face Pull", MuscleGroup.SHOULDERS, Equipment.CABLE,
            "1) Cuerda en polea alta, a la altura de la cara.\n" +
                "2) Tira hacia la frente separando las manos, codos altos.\n" +
                "3) Aprieta la parte alta de la espalda y los hombros posteriores.\n" +
                "4) Regresa controlado. Peso ligero-moderado, muchas reps.",
        ),
        ex(
            "Barbell Curl", MuscleGroup.BICEPS, Equipment.BARBELL,
            "1) De pie, barra con agarre supino a la anchura de los hombros.\n" +
                "2) Codos pegados al torso y fijos.\n" +
                "3) Flexiona subiendo la barra sin balancear la espalda.\n" +
                "4) Aprieta arriba y baja controlado hasta extender.",
        ),
        ex(
            "Hammer Curl", MuscleGroup.BICEPS, Equipment.DUMBBELL,
            "1) Mancuernas a los costados con agarre neutro (palmas enfrentadas).\n" +
                "2) Codos fijos; sube manteniendo el agarre tipo martillo.\n" +
                "3) Aprieta arriba sin girar la muñeca.\n" +
                "4) Baja lento y controlado.",
        ),
        // Legs
        ex(
            "Back Squat", MuscleGroup.QUADS, Equipment.BARBELL,
            "1) Barra sobre los trapecios, pies a la anchura de hombros, puntas algo abiertas.\n" +
                "2) Core firme; baja llevando cadera atrás y rodillas en línea con los pies.\n" +
                "3) Baja al menos hasta que los muslos queden paralelos, espalda neutra.\n" +
                "4) Empuja el suelo para subir sin que las rodillas se metan hacia dentro.",
        ),
        ex(
            "Leg Press", MuscleGroup.QUADS, Equipment.MACHINE,
            "1) Espalda y glúteos pegados al respaldo, pies a la anchura de hombros en la plataforma.\n" +
                "2) Suelta los seguros y baja controlado hasta ~90° de rodilla.\n" +
                "3) No dejes que la zona lumbar se despegue del asiento.\n" +
                "4) Empuja con el talón sin bloquear las rodillas de golpe.",
        ),
        ex(
            "Romanian Deadlift", MuscleGroup.HAMSTRINGS, Equipment.BARBELL,
            "1) De pie con la barra, rodillas levemente flexionadas y fijas.\n" +
                "2) Haz bisagra de cadera llevando el glúteo atrás, barra pegada a las piernas.\n" +
                "3) Baja hasta sentir estiramiento en isquios, espalda neutra.\n" +
                "4) Sube empujando la cadera al frente y aprieta glúteos arriba.",
        ),
        ex(
            "Leg Curl", MuscleGroup.HAMSTRINGS, Equipment.MACHINE,
            "1) Ajusta el rodillo justo por encima de los talones.\n" +
                "2) Flexiona las rodillas llevando los talones al glúteo.\n" +
                "3) Aprieta los isquios en el punto máximo.\n" +
                "4) Baja controlado sin dejar caer el peso.",
        ),
        ex(
            "Hip Thrust", MuscleGroup.GLUTES, Equipment.BARBELL,
            "1) Espalda alta apoyada en un banco, barra sobre la cadera (usa almohadilla).\n" +
                "2) Pies firmes, empuja con los talones subiendo la cadera.\n" +
                "3) Extiende hasta alinear hombros-cadera-rodillas y aprieta glúteos arriba.\n" +
                "4) Baja controlado sin arquear la lumbar.",
        ),
        ex(
            "Standing Calf Raise", MuscleGroup.CALVES, Equipment.MACHINE,
            "1) Hombros bajo las almohadillas, puntas de los pies en la plataforma.\n" +
                "2) Baja los talones sintiendo estiramiento en el gemelo.\n" +
                "3) Sube lo máximo sobre las puntas y aprieta 1 segundo.\n" +
                "4) Baja lento; controla todo el rango.",
        ),
        // Core
        ex(
            "Hanging Leg Raise", MuscleGroup.ABS, Equipment.BODYWEIGHT,
            "1) Cuélgate de la barra con brazos extendidos, sin balanceo.\n" +
                "2) Sube las piernas juntas llevando la pelvis hacia arriba (retroversión).\n" +
                "3) Evita usar impulso; controla la bajada.\n" +
                "4) Rodillas flexionadas si aún no dominas con piernas rectas.",
        ),
        ex(
            "Cable Crunch", MuscleGroup.ABS, Equipment.CABLE,
            "1) De rodillas frente a la polea alta con cuerda tras la nuca.\n" +
                "2) Flexiona el tronco llevando los codos hacia los muslos, redondeando la columna.\n" +
                "3) Aprieta el abdomen abajo; el movimiento viene del tronco, no de la cadera.\n" +
                "4) Sube controlado sin perder la tensión.",
        ),
        ex(
            "Plank", MuscleGroup.ABS, Equipment.BODYWEIGHT,
            "1) Apoya antebrazos y puntas de los pies, codos bajo los hombros.\n" +
                "2) Cuerpo en línea recta: aprieta abdomen y glúteos.\n" +
                "3) No dejes caer la cadera ni la subas.\n" +
                "4) Respira y mantén el tiempo objetivo; progresa aumentando segundos.",
        ),
    )
}
