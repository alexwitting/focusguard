plugins {
    id("focusguard.android.library")
    id("focusguard.compose")
    id("focusguard.hilt")
    id("focusguard.detekt")
    id("focusguard.kover")
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
}

// Regression gate, not a target — see :core:domain's build.gradle.kts for why.
kover {
    reports {
        verify {
            rule { minBound(75) }
        }
    }
}
