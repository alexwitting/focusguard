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
    testImplementation(project(":core:testing"))
}

// Fails CI below 90% line coverage.
kover {
    reports {
        verify {
            rule { minBound(90) }
        }
    }
}
