plugins {
    id("focusguard.android.application")
    id("focusguard.compose")
    id("focusguard.hilt")
    id("focusguard.detekt")
    alias(libs.plugins.kotlin.serialization)
    id("focusguard.kover")
}

android {
    namespace = "com.focusguard"
    defaultConfig {
        applicationId = "com.focusguard"
        versionCode = 1
        versionName = "1.0.0"
    }
    testOptions {
        // Robolectric needs merged resources (fonts, themes) to render Compose screenshots.
        unitTests.isIncludeAndroidResources = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:system"))
    implementation(project(":feature:home"))
    implementation(project(":feature:block"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:onboarding"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)

    implementation(libs.androidx.profileinstaller)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // README screenshots: Robolectric renders the screens, Roborazzi saves PNGs. Robolectric's
    // runner is JUnit4, run through the vintage engine.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit5.vintage.engine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// `./gradlew :app:testDebugUnitTest --tests "*ReadmeScreenshots*" -PrecordScreenshots`
// regenerates docs/screenshots/*.png. Without the flag those tests are skipped.
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", providers.gradleProperty("recordScreenshots").isPresent.toString())
    systemProperty("focusguard.screenshotDir", rootProject.file("docs/screenshots").absolutePath)
}

// Fails CI below 90% line coverage.
kover {
    reports {
        verify {
            rule { minBound(90) }
        }
    }
}
