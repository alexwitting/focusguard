plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover) apply false
    id("focusguard.kover")
}

// Kover's merged report needs every measured module listed explicitly here; applying the
// plugin at root is enough, submodules don't need focusguard.detekt-style per-module wiring.
// :core:testing is fakes-only (testImplementation, never compiled into the app) and
// :macrobenchmark is a benchmark harness, not app logic — both excluded from coverage.
// :core:ui is entirely Composable design-system code (theme tokens, AppIcon rendering) with
// no business logic once the @Composable exclusion in focusguard.kover applies — nothing
// left to measure, so it's excluded here too rather than dragging the aggregate to 0%.
// The Hilt-codegen exclusion filters live in focusguard.kover (build-logic), shared with
// every module's own report so per-module numbers aren't inflated by generated boilerplate.
dependencies {
    kover(project(":app"))
    kover(project(":core:data"))
    kover(project(":core:domain"))
    kover(project(":core:system"))
    kover(project(":feature:home"))
    kover(project(":feature:block"))
    kover(project(":feature:settings"))
    kover(project(":feature:onboarding"))
}

// Per-module rule { minBound(N) } gates (see each module's build.gradle.kts) all check LINE
// coverage — Kover's default coverageUnits — not BRANCH. This is a second, explicit gate on the
// merged report's BRANCH percentage specifically, added once a deliberate coverage push actually
// reached 85% branch coverage (aggregate report: 421/492). Same "regression gate, not a target"
// philosophy as the per-module LINE gates.
kover {
    reports {
        verify {
            rule {
                bound {
                    minValue = 85
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH
                }
            }
        }
    }
}
