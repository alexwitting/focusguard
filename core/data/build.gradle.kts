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

    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // UsageDaoTest runs Room's real SQL against an in-memory database. Robolectric's runner is
    // JUnit4-native, so (as in :core:system) that file opts into JUnit4 + the vintage engine.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit5.vintage.engine)
}

// Regression gate, not a target — see :core:domain's build.gradle.kts for why.
kover {
    reports {
        verify {
            rule { minBound(85) }
        }
    }
}
