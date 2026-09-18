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
- **Stage 3 — Premium UI (Jetpack Compose).** Theme (colors/typography/shapes), reusable
  glassmorphism components, and three screens — Dashboard (streak, AI panel, routine progress),
  Session tracking (real-time set logging + rest timer), Progress analysis (interactive cyan-on-gold
  load chart + history) — wired to Room and the local-AI flow via Hilt MVVM ViewModels. ✅

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
└── ui
    ├── theme          # Compose design system (black + gold + cyan, glassmorphism)
    ├── components     # GlassCard, GlowProgressBar, StatTile, AccentButton, …
    ├── navigation     # NavHost + routes
    ├── dashboard      # DashboardScreen + DashboardViewModel
    ├── session        # SessionScreen + SessionViewModel (set logging + rest timer)
    └── progress       # ProgressScreen + ProgressViewModel + LoadChart (Canvas)
```

## Screens (Stage 3)

- **Dashboard** — training streak, a live AI suggestion panel (on-device, streamed), and routine
  cards with weekly-progress bars.
- **Session tracking** — pick an exercise, log weight × reps in real time, auto-starting a
  rest timer (60/90/120s presets); sets grouped per exercise.
- **Progress analysis** — an interactive `LoadChart` (Canvas): a cyan curve with glow + area fill
  over a gold field, tap-to-select a point, plus a best-mark stat and detailed history.

All three are driven by Hilt `@HiltViewModel` ViewModels exposing `StateFlow<…UiState>` collected
with `collectAsStateWithLifecycle()`, reactively backed by Room `Flow`s and the AI use case.

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
- Room schemas are exported to `app/schemas/` (commit the generated JSON after a build).
- **Migrations:** real migrations live in `data/local/Migrations.kt` (empty at v1). Release
  builds require them; only debug builds fall back to a destructive recreate. When you bump the
  DB version, add the `Migration` there and validate it with `MigrationTestHelper`.
