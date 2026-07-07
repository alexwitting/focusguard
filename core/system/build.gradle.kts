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
        // Overrides AndroidLibraryConventionPlugin's default runner — instrumented tests here
        // let the real OS instantiate the @AndroidEntryPoint service classes, which runs the
        // real Hilt-generated injection path in onCreate() and needs a Hilt-capable test
        // Application (HiltTestApplication), unlike the unit tests which deliberately bypass
        // Hilt entirely (see FocusGuardAccessibilityServiceTest/DnsVpnServiceTest headers).
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
    // JUnit4 + vintage engine — Robolectric's own test runner is JUnit4-native and the
    // community JUnit5 bridge showed cross-test isolation issues on FocusGuardAccessibilityServiceTest
    // (assertions passed alone, failed as part of the class — see that file's header comment).
    // Scoped to files that opt in via @RunWith(RobolectricTestRunner::class); everything else
    // in this module stays on JUnit5/Jupiter.
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit5.vintage.engine)
    // Lets tests call DnsVpnService's private suspend fun handlePacket() via reflection
    // (KCallable.callSuspend) — the packet-decision logic isn't exposed any other way, and
    // that's the only part of the class testable without a real OS-level VPN interface.
    testImplementation(libs.kotlin.reflect)

    // Instrumented tests (connectedAndroidTest) for FocusGuardAccessibilityService/DnsVpnService
    // — real OS-level stability confidence Robolectric can't provide (real accessibility-service
    // registration, real event dispatch, real VPN consent). Uses the real production Hilt graph,
    // not fakes, since the point is genuine OS+DI integration confidence.
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
        // Regression gate, not a target — see :core:domain's build.gradle.kts for why.
        verify {
            rule { minBound(84) }
        }
    }
}
