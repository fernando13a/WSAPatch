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

- **Stage 1 — Data & persistence.** Package structure, Room entities, relations,
  DAO, DI wiring, seed catalog. ✅
- **Stage 2 — On-device AI.** MediaPipe LLM Inference (`LlmInferenceManager`) streaming a
  `Flow<String>`, `GetProgressionSuggestionUseCase` (Room history → coach prompt → progressive
  overload suggestion), and a `SuggestionState` sealed class (Loading / Success / Error). ✅
- **Stage 3 — UI.** Full glassmorphism screens, MVVM ViewModels, navigation, and the model
  download/placement flow.

## Project structure (Clean Architecture)

```
com.ironmind.app
├── core/util          # DispatcherProvider, Constants — cross-cutting helpers
├── domain             # Pure Kotlin — no Android/Room/MediaPipe dependencies
│   ├── model          # Exercise, Routine, WorkoutSession, SetLog, enums, SuggestionState
│   ├── ai             # LlmInferenceService (port), ProgressionPromptBuilder, exceptions
│   ├── repository     # WorkoutRepository (the contract the app depends on)
│   └── usecase        # GetProgressionSuggestionUseCase
├── data               # Implements the domain contracts
│   ├── local
│   │   ├── entity     # @Entity: Exercise, Routine, RoutineExerciseCrossRef, Session, SetLog
│   │   ├── relation   # RoutineWithExercises, SessionWithSets
│   │   ├── converter  # Enum <-> String TypeConverters
│   │   ├── dao        # WorkoutDao (Flow reads, suspend writes, @Transaction relations)
│   │   ├── seed       # DefaultExercises — starter catalog on first launch
│   │   └── IronMindDatabase.kt
│   ├── ai             # LlmInferenceManager (MediaPipe), AiConstants
│   ├── mapper         # entity <-> domain mappers
│   └── repository     # WorkoutRepositoryImpl
├── di                 # Hilt modules: Database, Repository, Coroutines, Ai
└── ui/theme           # Compose design system (black + gold + cyan, glassmorphism)
```

## On-device AI (Stage 2)

- **`LlmInferenceManager`** (`data/ai`) implements the domain port **`LlmInferenceService`**. It
  lazily loads the MediaPipe engine on `Dispatchers.IO`, opens a session per request, and exposes
  the streaming response as a `Flow<String>` via `callbackFlow` (typewriter effect).
- **`GetProgressionSuggestionUseCase`** (`domain/usecase`) reads the recent history for an
  exercise from Room, builds a structured Spanish coaching prompt with
  **`ProgressionPromptBuilder`**, streams the model's answer, and emits **`SuggestionState`**
  (`Loading` → accumulating `Success` → final `Success` / `Error`).
- **Model file:** place a MediaPipe-compatible model (e.g. a Gemma `.bin`/`.task`) at
  `filesDir/models/` (see `AiConstants.MODEL_FILE_NAME`). Kept out of the APK to stay small while
  running fully offline. Missing model → a friendly `Error` state (`LlmModelNotFoundException`).

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
./gradlew testDebugUnitTest    # JVM unit tests (mappers, prompt builder, suggestion use case)
./gradlew connectedDebugAndroidTest   # instrumented Room DAO tests (device/emulator)
```

- **Min SDK:** 26 · **Target/Compile SDK:** 35 · **JDK:** 17
- Room schemas are exported to `app/schemas/` for future migrations.
