plugins {
    id("focusguard.android.library")
    id("focusguard.compose")
    id("focusguard.hilt")
    id("focusguard.detekt")
    id("focusguard.kover")
    alias(libs.plugins.robolectric.junit5)
}

android {
    namespace = "com.focusguard.feature.onboarding"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // Robolectric sets the SDK level for the Android 12+ steps.
    testImplementation(libs.robolectric)
    testImplementation(libs.robolectric.junit5.extension)
}

// Fails CI below 90% line coverage.
kover {
    reports {
        verify {
            rule { minBound(90) }
        }
    }
}
