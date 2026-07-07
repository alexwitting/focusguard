plugins {
    id("focusguard.kotlin.jvm")
    id("focusguard.detekt")
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.junit5.api)
    implementation(libs.turbine)
    implementation(libs.mockk)
}
