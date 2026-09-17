# IronMind 🏋️‍♂️🧠

Premium, **100% offline** gym-tracking app for Android with **on-device AI** (Google AI Edge /
MediaPipe LLM Inference). Built with a strict, modern Android stack.

> **Note on the app name:** `IronMind` (package `com.ironmind.app`) is a working title chosen so
> the project could be scaffolded end-to-end. It is trivial to rename — just say the word and it
> gets updated before Stage 2.

## Tech stack

| Layer            | Choice                                                         |
|------------------|---------------------------------------------------------------|
| Language         | Kotlin                                                         |
| UI               | Jetpack Compose (Material 3)                                   |
| Architecture     | Clean Architecture + MVVM + Hilt (DI)                          |
| Persistence      | Room (SQLite) with Flow + Coroutines                           |
| On-device AI     | Google AI Edge — MediaPipe LLM Inference API (streaming) — *Stage 3* |

## Design language

Pure-black (`#000000`) surface, **glassmorphism** cards with subtle glowing edges, and dynamic
accents in **gold `#FFD700`** and **cyan `#00FFFF`**. Tokens live in
`ui/theme/` (`Color.kt`, `Theme.kt`, `Type.kt`).

## Roadmap

- **Stage 1 — Data & persistence (this commit).** Package structure, Room entities, relations,
  DAO, DI wiring, seed catalog. ✅
- **Stage 2 — Domain & presentation.** Use cases, MVVM ViewModels, state, navigation.
- **Stage 3 — UI + on-device AI.** Full glassmorphism screens and the streaming MediaPipe LLM.

## Project structure (Clean Architecture)

```
com.ironmind.app
├── core/util          # DispatcherProvider, Constants — cross-cutting helpers
├── domain             # Pure Kotlin — no Android/Room dependencies
│   ├── model          # Exercise, Routine, WorkoutSession, SetLog, enums, aggregates
│   └── repository     # WorkoutRepository (the contract the app depends on)
├── data               # Implements the domain contracts
│   ├── local
│   │   ├── entity     # @Entity: Exercise, Routine, RoutineExerciseCrossRef, Session, SetLog
│   │   ├── relation   # RoutineWithExercises, SessionWithSets
│   │   ├── converter  # Enum <-> String TypeConverters
│   │   ├── dao        # WorkoutDao (Flow reads, suspend writes, @Transaction relations)
│   │   ├── seed       # DefaultExercises — starter catalog on first launch
│   │   └── IronMindDatabase.kt
│   ├── mapper         # entity <-> domain mappers
│   └── repository     # WorkoutRepositoryImpl
├── di                 # Hilt modules: Database, Repository, Coroutines
└── ui/theme           # Compose design system (black + gold + cyan, glassmorphism)
```

## Data model

- **ExerciseEntity** — catalog of exercises (name, muscle group, equipment, custom flag).
- **RoutineEntity** — a training day with a `RoutineSplit` (PUSH / PULL / LEGS / UPPER / LOWER / …).
- **RoutineExerciseCrossRef** — many-to-many junction with per-routine prescription
  (target sets / reps / rest) and ordering.
- **WorkoutSessionEntity** — a performed session, optionally linked to a routine.
- **SetLogEntity** — a single logged set: `weightKg`, `reps`, `rpe`, and a free-form `notes`
  field for supplements (e.g. *Optimum Nutrition Gold Standard 100% Whey*), energy levels, etc. —
  context the on-device AI will summarize in Stage 3.

Relations: **`RoutineWithExercises`** and **`SessionWithSets`**.

## Building

Requires Android Studio (Ladybug+) or the Android SDK with `ANDROID_HOME` set.

```bash
cd IronMind
./gradlew assembleDebug        # build the APK
./gradlew testDebugUnitTest    # JVM unit tests (mappers)
./gradlew connectedDebugAndroidTest   # instrumented Room DAO tests (device/emulator)
```

- **Min SDK:** 26 · **Target/Compile SDK:** 35 · **JDK:** 17
- Room schemas are exported to `app/schemas/` for future migrations.
