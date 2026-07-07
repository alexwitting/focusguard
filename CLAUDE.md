# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**FocusGuard** — open-source Android app (package `com.focusguard`) that blocks apps, websites, and short-form content, enforces daily time limits, and reports usage.

`minSdk 26`, `targetSdk 35`. `java.time` classes (`LocalDate`, `Instant`, `Duration`) work natively — no desugaring needed.

## Build & Test Commands

```bash
# Build
./gradlew assembleDebug

# Run unit tests for a specific module
./gradlew :core:domain:test
./gradlew :core:system:test

# Run a single test class
./gradlew :core:domain:test --tests "com.focusguard.core.domain.model.PinVerificationTokenTest"

# Run all unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Android Lint
./gradlew lint

# ktlint — check formatting / auto-fix
./gradlew ktlintCheck
./gradlew ktlintFormat

# detekt — static analysis (config: config/detekt/detekt.yml)
./gradlew detekt
# regenerate a module's baseline after intentionally accepting new findings:
./gradlew detektBaseline

# Coverage (Kover) — aggregated report at build/reports/kover/html/index.html
./gradlew koverHtmlReport
./gradlew koverXmlReport

# Clean
./gradlew clean
```

## Module Structure

```
:app                  — Hilt root (@HiltAndroidApp), NavGraph, MainActivity, BlockedActivity
:feature:home         — Usage dashboard (depends on :core:domain, :core:ui only)
:feature:block        — Block list configuration
:feature:settings     — Time limits, PIN lock, permissions health
:feature:onboarding   — First-run permission grant flow
:core:domain          — Pure Kotlin JVM — models, repository interfaces, UseCases. ZERO Android deps.
:core:data            — Repository impls, Room DB, DataStore, UsageStats client
:core:ui              — Material 3 theme, shared Composables, design tokens
:core:system          — AccessibilityService, VpnService, WorkManager, AlarmManager
:core:testing         — Shared fakes (FakeBlockRuleRepository etc.) — testImplementation only, never compile
:macrobenchmark       — Baseline profile generation + startup benchmark, targets :app
build-logic/          — Convention Gradle plugins
```

**Dependency rule:** features depend on `:core:domain` and `:core:ui` only. Features never depend on each other. `:app` is the only module allowed to depend on everything (it wires the NavGraph and hosts `BlockedActivity`, which is Android-entry-point code, not a feature screen, so its ViewModel lives in `:app` too rather than in a feature module).

## Architecture — MVI

```
UI (Compose)  →Intent→  ViewModel  →  UseCases  →  Repository interfaces
                ↑State/Effect↑                          ↓
                                              Repository implementations (:core:data)
                                                          ↓
                                                  Room / DataStore / System APIs
```

- Each screen state is one `data class`. ViewModels expose `StateFlow<ScreenState>` and `SharedFlow<Effect>` (one-shot: navigate, snackbar) via `MutableSharedFlow` + `asSharedFlow()` — never a raw `Channel`.
- No LiveData anywhere.
- All blocking decision logic lives in pure-Kotlin engine classes (`AppBlockingEngine`, `DnsFilterEngine`), never directly in `AccessibilityService` or `VpnService`.
- **Error handling:** mutation methods wrap the call in `runCatching { ... }.onSuccess { }.onFailure { Log.e(TAG, "...", it); emit(Effect.ShowSnackbar(...)) }`. A `combine(...).collect { }` in `init` that drives the screen's primary state should have a `.catch { }` upstream of `collect` — an uncaught exception there silently ends the flow forever (the screen freezes with no further updates and no error shown), not just for that one tick. See `SystemHealthRepositoryImpl`'s polling loops for the same reasoning applied to a repository-level `flow { while (true) { ... } }`.
- **Time/date access:** business logic never calls `LocalDate.now()` / `LocalDateTime.now()` / `System.currentTimeMillis()` directly — inject `ClockProvider` (`:core:domain/time/ClockProvider.kt`) instead, and use `FakeClockProvider` (`:core:testing`) in tests. This exists because untestable direct time calls previously caused real midnight-rollover bugs (see `BlockViewModel`'s `dateTicker()` comment). The two sanctioned exceptions are pure UI display code (e.g. a Composable just rendering "today's date") and one-shot alarm-scheduling math that's about real wall-clock time by definition (`MidnightResetWorker`'s next-midnight delay calculation).

## Convention Plugins (build-logic)

| Plugin ID | Applied to | Does |
|---|---|---|
| `focusguard.kotlin.jvm` | `:core:domain`, `:core:testing` | Kotlin JVM, Java 17, `useJUnitPlatform()` |
| `focusguard.android.library` | all other `:core:*` + `:feature:*` | Android library, compileSdk 35, minSdk 26 |
| `focusguard.android.application` | `:app` | Android application, same SDK |
| `focusguard.hilt` | any module using Hilt | applies Hilt + KSP plugins, adds hilt-android + compiler deps |
| `focusguard.compose` | any module with Compose | applies compose plugin, enables buildFeatures.compose |
| `focusguard.detekt` | every module | applies detekt + ktlint, points detekt at `config/detekt/detekt.yml` + a per-module baseline |

Kover (coverage) and the raw `ktlint`/`detekt` plugins are declared `apply false` at the root `build.gradle.kts` so their classes are available to every module's buildscript classpath — see that file for why `apply(plugin = "id")` doesn't work for included-build plugin IDs from a `subprojects {}` block, only the `plugins {}` DSL does.

## Key Domain Models

Defined in `:core:domain/src/main/kotlin/com/focusguard/core/domain/`:

- `BlockingStrategy` — sealed class: `AlwaysBlock`, `ScheduledBlock(windows)`, `TimeLimit(dailyLimitMinutes)`
- `TimeWindow` — hour/minute range + `Set<DayOfWeek>` (validated in `init`)
- `AppBlockRule` — packageName + label + `BlockingStrategy` + enabled flag
- `WebBlockRule` — eTLD+1 domain + blockSubdomains flag
- `ContentFilter` — enum: `REELS`, `SHORTS`, `TIKTOK_FYP`
- `BlockDecision` — sealed: `Allow` or `Block(BlockReason)`
- `PinVerificationToken` — `issuedAt: Instant`, 5-min expiry, `isValid` computed property
- `AppWithRule`, `AppUsageSummary`, `DailyTotal`, `PermissionsHealth`, `SystemHealth`

Also in `:core:domain` but not a data model — `ClockProvider` (`time/ClockProvider.kt`): the current-time seam described under Architecture — MVI above.

## DI Module Organization (`:core:data/di`)

- `DatabaseModule` — Room DB, DAOs, DataStore, `EncryptedSharedPreferences`. Persistence only; a raw Android system service with no persistence angle (e.g. `PackageManager`) does not belong here.
- `RepositoryModule` — one `@Binds @Singleton` per repository interface, including `ClockProvider` (bound to `SystemClockProvider`).
- `SystemServiceModule` — raw Android system-service `@Provides` functions that aren't repository- or persistence-shaped (currently just `PackageManager`).
- Each feature module owns its own `di/<Feature>UseCaseModule.kt` with one `@Provides` per UseCase it needs, constructed from repository interfaces (and `ClockProvider` where relevant) — UseCases themselves are plain classes, not `@Inject`-constructed, so they're wired explicitly here rather than relying on Hilt's constructor injection.
- `:app`-only UseCases (e.g. `RequestExtensionUseCase`, used by `BlockedViewModel`) get their own small module under `app/.../di/` following the same one-module-per-concern pattern rather than being bolted onto an unrelated module.

## PIN Gate Pattern

All UseCase mutations that modify rules accept `token: PinVerificationToken?`. The token is issued by `VerifyPinUseCase` after a correct PIN entry and is valid for 5 minutes. When PIN is disabled, `token` may be null. The gate check is in the domain layer, not UI.

## Tech Stack

| Concern | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose (type-safe `@Serializable` routes) |
| DI | Hilt (compile-time verified) |
| Database | Room + KSP |
| Preferences | DataStore (Preferences API) |
| Encrypted storage | `EncryptedSharedPreferences` — PIN hash only |
| Charts | Vico 2.x |
| DNS blocking | `dnsjava` (`org.xbill.dns`) — no hand-written DNS parsing |
| Background | WorkManager + AlarmManager |
| Unit testing | JUnit 5 + MockK + Turbine + Robolectric |
| Instrumented | JUnit 4 + AndroidX Test (`core`/`runner`/`rules`) + UI Automator + `hilt-android-testing` |

Versions live in `gradle/libs.versions.toml`. Always add deps there, not inline.

## Current State

All layers are implemented and wired end to end: `:core:domain` UseCases/models, `:core:data` Room+DataStore repositories, `:core:system` (`FocusGuardAccessibilityService`, `DnsVpnService`, WorkManager pollers, `TimeLimitScheduler`), and all four feature screens (Home, Block, Settings, Onboarding) plus `BlockedActivity`. CI (`.github/workflows/focusguard.yml`) runs `ktlintCheck detekt` + `test` + coverage on every push/PR to `master`; a manual `workflow_dispatch` toggle can additionally build and upload the debug APK. Instrumented tests are not yet wired into CI — run them locally via `connectedAndroidTest`.

Work generally proceeds bug/feature-at-a-time now rather than in the original phase order — when picking up new work, `git log` and the module structure above are the source of truth, not a phase number.

## Testing Conventions

- JUnit 5 (`@Test` from `org.junit.jupiter.api`) in all JVM modules (`:core:domain`, `:core:system`, `:core:testing`), except files that opt into Robolectric's own JUnit4 `@RunWith(RobolectricTestRunner::class)` runner directly (see `FocusGuardAccessibilityServiceTest`'s header comment for why that one file departs from the JUnit5 bridge).
- JUnit 4 for Android instrumented tests (`core/system/src/androidTest`) — currently covers `FocusGuardAccessibilityService`'s real OS-integration behavior (real accessibility-service binding, real event dispatch, real back-navigation, real node-tree walk). Run via `./gradlew :core:system:connectedDebugAndroidTest`; needs a device/emulator. If multiple devices are attached, set `ANDROID_SERIAL` first — these tests toggle real system accessibility settings.
- MockK for mocking; `flowOf(...)` to stub Flow-returning repository methods; `Fake*Repository`/`FakeClockProvider` (`:core:testing`) for sociable ViewModel tests instead of mocking every dependency.
- Turbine for Flow assertions: `flow.test { assertEquals(..., awaitItem()) }`
- `runTest` from `kotlinx.coroutines.test` for suspend functions
- A ViewModel method that launches on `Dispatchers.IO` explicitly (e.g. `forceRefresh()`) isn't touched by `MainDispatcherExtension` (which only swaps `Dispatchers.Main`) — poll with a bounded real-time `awaitCondition { ... }` helper instead of asserting immediately; see `SettingsViewModelTest`/`OnboardingViewModelTest` for the pattern.
- Engine classes (`AppBlockingEngine`, `DnsFilterEngine`) target 100% branch coverage.
- Kover per-module `minBound` gates are a **regression floor, not a target** — set just below currently-measured coverage so an accidental drop fails CI without forcing coverage-chasing busywork. Don't ratchet a gate above 85%; the aggregate root `build.gradle.kts` also gates merged branch coverage at 85%. If a change legitimately raises a module's coverage, it's fine to leave the old (lower) gate in place rather than bumping it every time.
