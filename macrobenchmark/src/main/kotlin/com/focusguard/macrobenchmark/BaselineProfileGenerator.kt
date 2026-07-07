package com.focusguard.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for the :app module.
 *
 * Run on a rooted physical device or a Gradle-managed emulator (API 33+):
 *   ./gradlew :macrobenchmark:generateBaselineProfile
 *
 * The output lands at:
 *   macrobenchmark/build/outputs/managed_device_android_test_additional_output/
 *
 * Copy the resulting baseline-prof.txt to app/src/main/baseline-prof.txt
 * and commit it so every release build gets ART pre-compilation.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = "com.focusguard") {
            pressHome()
            startActivityAndWait()
            // Allow the home screen composable to fully render its state.
            device.waitForIdle()
        }
    }
}
