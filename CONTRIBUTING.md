# Contributing to FocusGuard

Thanks for your interest in contributing.

## Development setup

1. Install [Android Studio](https://developer.android.com/studio) (Ladybug or newer)
2. Clone the repo and open the root directory
3. Android Studio will sync Gradle automatically
4. No API keys or external services are required

**SDK path:** Android Studio sets this automatically. Do not commit `local.properties`.

## Build & test commands

```bash
# Debug build
./gradlew assembleDebug

# Unit tests for a specific module
./gradlew :core:domain:test
./gradlew :core:system:test

# Single test class
./gradlew :core:domain:test --tests "com.focusguard.core.domain.model.PinVerificationTokenTest"

# All unit tests
./gradlew test

# Instrumented tests (requires a connected device or emulator; if more than one is
# attached, set ANDROID_SERIAL first — these tests toggle real system settings)
./gradlew :core:system:connectedDebugAndroidTest

# Lint
./gradlew lint

# ktlint — check formatting / auto-fix
./gradlew ktlintCheck
./gradlew ktlintFormat

# detekt — static analysis
./gradlew detekt

# Coverage report → build/reports/kover/html/index.html
./gradlew koverHtmlReport
# Verify coverage gates (also runs as part of CI)
./gradlew koverVerify

# Clean
./gradlew clean
```

## Module dependency rules

- `:feature:*` modules depend on `:core:domain` and `:core:ui` only — never on each other or on `:core:data`/`:core:system`
- `:core:domain` is pure Kotlin JVM — zero Android dependencies
- `:core:testing` is `testImplementation` only — never `implementation`
- `:app` is the only module that may depend on everything (it hosts the NavGraph and `BlockedActivity`)

## Code conventions

- Architecture: Clean Architecture + MVI (`StateFlow<ScreenState>` + `SharedFlow<Effect>`, via `MutableSharedFlow`/`asSharedFlow()` — not a raw `Channel`)
- No LiveData; no direct ViewModel-to-repository calls (always go through UseCases)
- Blocking logic lives in pure-Kotlin engine classes (`AppBlockingEngine`, `DnsFilterEngine`), never in services directly
- All mutations that change rules require a `PinVerificationToken` when PIN is enabled
- Add dependencies to `gradle/libs.versions.toml`, never inline in build files
- Never call `LocalDate.now()`/`LocalDateTime.now()`/`System.currentTimeMillis()` directly from business logic — inject `ClockProvider` (`:core:domain`) so tests can control time via `FakeClockProvider` (`:core:testing`)
- Wrap ViewModel mutations in `runCatching { }.onSuccess { }.onFailure { }`, and add `.catch { }` to any `Flow` driving a screen's primary state — an uncaught exception there ends the flow permanently, not just for one update
- See `CLAUDE.md` for the full architecture rationale and DI module organization conventions

## Testing

- JUnit 5 + MockK + Turbine for all JVM modules (`:core:domain`, `:core:system`, `:core:testing`, feature modules)
- Robolectric for JVM tests that need real Android framework behavior (e.g. `FocusGuardAccessibilityServiceTest`)
- JUnit 4 for instrumented tests (`core/system/src/androidTest`) — run via `:core:system:connectedDebugAndroidTest`
- Engine classes (`AppBlockingEngine`, `DnsFilterEngine`) target 100% branch coverage
- Kover's per-module coverage gates are a regression floor (fail if coverage drops), not a target to chase upward

## Submitting a pull request

1. Fork the repository and create a branch from `master`
2. Make your changes with tests where applicable
3. Ensure `./gradlew ktlintCheck detekt test` passes (this is what CI runs on every push/PR)
4. Open a pull request with a clear description of the change and why it's needed

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
