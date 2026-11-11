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

// Modules in the merged coverage report. Excluded: :core:testing (test fakes), :macrobenchmark
// (benchmark harness) and :core:ui (Compose only, nothing left once composables are excluded).
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

// Per-module gates check line coverage; this one fails CI below 90% branch coverage of the
// merged report.
kover {
    reports {
        verify {
            rule {
                bound {
                    minValue = 90
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH
                }
            }
        }
    }
}
