plugins {
    id("focusguard.android.library")
    id("focusguard.compose")
    id("focusguard.detekt")
    id("focusguard.kover")
}

android {
    namespace = "com.focusguard.core.ui"
}

dependencies {
    implementation(project(":core:domain"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.vico.compose.m3)
}
