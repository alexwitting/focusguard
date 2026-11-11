plugins {
    id("focusguard.android.library")
    id("focusguard.hilt")
    id("focusguard.detekt")
    alias(libs.plugins.kotlin.serialization)
    id("focusguard.kover")
}

android {
    namespace = "com.focusguard.core.data"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.security.crypto)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // UsageDaoTest runs Room's SQL against an in-memory database under Robolectric's JUnit4
    // runner, through the vintage engine.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit5.vintage.engine)
}

// Fails CI below 90% line coverage.
kover {
    reports {
        verify {
            rule { minBound(90) }
        }
    }
}
