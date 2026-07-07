plugins {
    id("focusguard.kotlin.jvm")
    id("focusguard.detekt")
    id("focusguard.kover")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
}

// Regression gate, not a target: set just below the currently-measured line coverage so this
// catches an accidental coverage drop without blocking `check`/CI or chasing a number upward.
kover {
    reports {
        verify {
            rule { minBound(85) }
        }
    }
}
