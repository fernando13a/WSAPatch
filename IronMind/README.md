# IronMind 🏋️‍♂️🧠

Premium, **100% offline** gym-tracking app for Android with an **on-device AI coach** (Google AI
Edge / MediaPipe LLM Inference). No account, no backend, no telemetry — everything runs on the phone.

> **Note on the app name:** `IronMind` (package `com.ironmind.app`) is a working title. It is trivial
> to rename — just say the word and it gets updated across the project.

## Highlights

- 🔒 **100% offline & private.** All data lives in a local Room database. The AI runs on-device.
  Nothing leaves the phone.
- 🧠 **On-device AI coach.** A MediaPipe LLM (e.g. Gemma) reads your recent history for an exercise
  and streams a progressive-overload suggestion — in Spanish, generated locally.
- 📚 **870+ exercise catalog.** A curated set of 23 exercises with Spanish how-to guides, plus a
  large public-domain catalog (from [free-exercise-db](https://github.com/yuhonas/free-exercise-db),
  The Unlicense) seeded on first launch.
- 🖼️ **Reference images & GIFs.** On-demand, Coil-cached public-domain demo images per exercise, plus
  the option to attach your own photo or animated GIF (kept on-device).
- ⏱️ **Rest timer that survives backgrounding.** A hero circular countdown dial on-screen, mirrored
  into a notification so the buzz reaches you even with the app in your pocket.
- 💾 **Backup & restore.** Export all your data to a JSON file and re-import it on another device —
  entirely on your phone.
- 🌐 **Spanish-first, bilingual.** Full Spanish UI (default) with an English translation; the exercise
  taxonomy (muscle groups, equipment, splits) is localized too.
- ⚖️ **kg / lb.** Pick your unit in Settings; data is always stored in kg and only the display/input
  converts, so every weight, chart, 1RM and the plate calculator follow your choice.
- 📷 **Weight from a photo.** Snap the plates or the dumbbell and on-device OCR (ML Kit, bundled
  model, no network) reads the printed numbers. You confirm the readings, pick whether they're
  plates-per-side or a direct value, and whether they're marked in kg or lb — the app totals it
  (bar + 2 × plates) and converts to your unit.

## Tech stack

| Layer            | Choice                                                          |
|------------------|----------------------------------------------------------------|
| Language         | Kotlin                                                          |
| UI               | Jetpack Compose (Material 3)                                    |
| Architecture     | Clean Architecture + MVVM + Hilt (DI)                          |
| Persistence      | Room (SQLite) with Flow + Coroutines, `@Migration`, exportSchema |
| On-device AI     | Google AI Edge — MediaPipe LLM Inference API (streaming)        |
| Images           | Coil (`coil-compose` + `coil-gif`) — cached, GIF/WebP-aware     |
| Serialization    | kotlinx.serialization (backup + bundled catalog)               |

## Design language — "Kinetic Glass Obsidian"

Pure-black (`#000000`) OLED surface, **glassmorphism** cards with glowing gold→cyan gradient edges,
and dynamic accents in **gold `#FFD700`** (discipline / PRs / CTAs) and **cyan `#00FFFF`** (live
telemetry / timers). Typography pairs **Space Grotesk** (titles, numeric readouts, labels) with
**Manrope** (body copy). Tokens live in `ui/theme/` (`Color.kt`, `Type.kt`, `Fonts.kt`, `Theme.kt`).

## Screens

- **Dashboard** — a "NEURAL STRENGTH ENGINE" header with on-device-AI / offline chips, streak &
  session stat tiles, a live AI suggestion panel (streamed on-device), and routine cards with
  weekly-progress bars. A first-launch prompt offers to download the AI model.
- **Session tracking** — log weight × reps in real time; a **circular rest-timer dial** with
  60/90/120s presets (auto-starting after each set) that also fires a notification; sets grouped per
  exercise, each linking to its detail/how-to.
- **Exercise detail** — muscle group & equipment (localized), a reference image (your own, or a
  cached public-domain demo, GIF-aware), and the written how-to guide.
- **Progress analysis** — an interactive `LoadChart` (Canvas): a cyan curve with glow + area fill
  over a gold field, tap-to-select a point, a best-mark stat, and detailed history.
- **Routine editor** — build routines from the catalog (localized split / muscle / equipment
  pickers) or create custom exercises.
- **Backup** and **Model download/import** — data export/restore and one-tap (or from-file) model
  provisioning.

All screens are driven by Hilt `@HiltViewModel` ViewModels exposing `StateFlow<…UiState>` collected
with `collectAsStateWithLifecycle()`, reactively backed by Room `Flow`s and the AI use case.

## On-device AI

- **`LlmInferenceManager`** (`data/ai`) implements the domain port **`LlmInferenceService`**. It
  lazily loads the MediaPipe engine on `Dispatchers.IO`, opens a session per request, and exposes
  the streaming response as a `Flow<String>` via `callbackFlow` (typewriter effect).
- **`GetProgressionSuggestionUseCase`** (`domain/usecase`) reads the recent history for an exercise
  from Room, builds a structured Spanish coaching prompt with **`ProgressionPromptBuilder`**, streams
  the model's answer, and emits **`SuggestionState`** (`Loading` → accumulating `Success` → final
  `Success` / `Error`).
- **Getting the model.** The AI coach needs a MediaPipe-compatible model (e.g. a Gemma `.task`).
  The app can **download it on first launch** from a public URL (`AiConstants.DEFAULT_MODEL_URL`),
  or you can **import a file** already on the phone. It is stored under `filesDir/models/` and kept
  out of the APK so the download stays optional and the app small. A missing model degrades
  gracefully to a friendly prompt (`LlmModelNotFoundException`).

## Data model & backup

- **ExerciseEntity** — catalog exercise: name, `MuscleGroup`, `Equipment`, custom flag, plus
  `instructions`, a user `imagePath` and a demo `imageUrl`.
- **RoutineEntity** — a training day with a `RoutineSplit` (PUSH / PULL / LEGS / UPPER / LOWER / …).
- **RoutineExerciseCrossRef** — many-to-many junction with per-routine prescription
  (target sets / reps / rest) and ordering.
- **WorkoutSessionEntity** — a performed session, optionally linked to a routine.
- **SetLogEntity** — a single logged set: `weightKg`, `reps`, `rpe`, and free-form `notes`.

Relations: **`RoutineWithExercises`** and **`SessionWithSets`**. Schema is at **version 3** with real
migrations in `data/local/Migrations.kt`. **Backup**: `RoomBackupManager` exports a
`@Serializable BackupSnapshot` to JSON and restores it in a single `withTransaction`.

## Project structure (Clean Architecture)

```
com.ironmind.app
├── core/util          # DispatcherProvider, Constants — cross-cutting helpers
├── domain             # Pure Kotlin — no Android/Room/MediaPipe dependencies
│   ├── model          # Exercise, Routine, WorkoutSession, SetLog, enums, SuggestionState
│   ├── ai             # LlmInferenceService (port), ProgressionPromptBuilder, exceptions
│   ├── backup         # BackupManager (export/import contract)
│   ├── repository     # WorkoutRepository (the contract the app depends on)
│   └── usecase        # GetProgressionSuggestionUseCase
├── data               # Implements the domain contracts
│   ├── local          # entities, relations, converters, DAO, migrations, seed, database
│   ├── ai             # LlmInferenceManager (MediaPipe), ModelDownloader, AiConstants
│   ├── backup         # BackupSnapshot (DTOs + mappers), RoomBackupManager
│   ├── preferences    # AppPreferences (first-launch model prompt, …)
│   ├── mapper         # entity <-> domain mappers
│   └── repository     # WorkoutRepositoryImpl
├── notification       # RestTimerNotifier (+ Android impl) — background rest alerts
├── di                 # Hilt modules: Database, Repository, Coroutines, Ai, Notification, Prefs
└── ui
    ├── theme          # Compose design system (Kinetic Glass Obsidian)
    ├── components     # GlassCard, GlowProgressBar, CircularRestTimer, AccentButton, Chip, …
    ├── util           # EnumLabels — localized taxonomy labels
    ├── navigation     # NavHost + routes
    ├── dashboard      # DashboardScreen + DashboardViewModel
    ├── session        # SessionScreen + SessionViewModel (set logging + rest timer)
    ├── exercise       # ExerciseDetailScreen + ViewModel (how-to, images, GIF)
    ├── routineedit    # RoutineEditScreen + ViewModel
    ├── backup         # BackupScreen + ViewModel
    ├── model          # ModelDownloadScreen + ViewModel
    └── progress       # ProgressScreen + ProgressViewModel + LoadChart (Canvas)
```

## Building

Requires Android Studio (Ladybug+) or the Android SDK with `ANDROID_HOME` set.

```bash
cd IronMind
./gradlew assembleDebug        # build the debug APK
./gradlew assembleRelease      # R8/minified release build
./gradlew testDebugUnitTest    # JVM unit tests (mappers, prompt builder, backup, use case)
./gradlew connectedDebugAndroidTest   # instrumented Room DAO + backup tests (device/emulator)
```

- **Min SDK:** 26 · **Target/Compile SDK:** 35 · **JDK:** 17
- Room schemas are exported to `app/schemas/`. When you bump the DB version, add the `Migration` in
  `data/local/Migrations.kt` and validate it with `MigrationTestHelper`.

## Continuous integration & downloading the APK

CI runs on GitHub Actions (`.github/workflows/android-ironmind.yml`) on every push:

- **build** — unit tests, `assembleDebug`, `assembleRelease` (R8), uploads both APKs as artifacts,
  and publishes the debug APK to a rolling `app-debug` GitHub Release.
- **instrumented** — spins up an API 29 emulator (KVM-accelerated) and runs the connected tests.

The latest debug APK is always available at:
**https://github.com/fernando13a/WSAPatch/releases/download/app-debug/app-debug.apk**

## Credits & licensing

- Exercise data & demo images: [free-exercise-db](https://github.com/yuhonas/free-exercise-db)
  (The Unlicense / public domain).
- Fonts: **Space Grotesk** and **Manrope** (SIL Open Font License 1.1).
- No copyrighted media is bundled; demo images are fetched on demand and cached.
