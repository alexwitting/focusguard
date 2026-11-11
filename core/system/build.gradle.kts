plugins {
    id("focusguard.android.library")
    id("focusguard.hilt")
    id("focusguard.detekt")
    id("focusguard.kover")
    alias(libs.plugins.robolectric.junit5)
}

android {
    namespace = "com.focusguard.core.system"
    defaultConfig {
        // Instrumented tests let the OS create the real @AndroidEntryPoint services, which needs
        // a Hilt test application.
        testInstrumentationRunner = "com.focusguard.core.system.HiltTestRunner"
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.dnsjava)
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.junit5.params)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric.junit5.extension)
    testImplementation(libs.workmanager.testing)
    // JUnit4 + vintage engine for files using Robolectric's own runner, which isolates tests
    // better than the JUnit5 bridge. Everything else stays on JUnit5.
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit5.vintage.engine)
    // Lets tests call DnsVpnService's private suspend handlePacket() through reflection.
    testImplementation(libs.kotlin.reflect)

    // Instrumented tests of the accessibility and VPN services against the real OS and the
    // production Hilt graph.
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.uiautomator)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

kover {
    reports {
        // Fails CI below 90% line coverage.
        verify {
            rule { minBound(90) }
        }
    }
}
