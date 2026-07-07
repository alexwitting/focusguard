plugins {
    `kotlin-dsl`
}

group = "com.focusguard.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("focusguardKotlinJvm") {
            id = "focusguard.kotlin.jvm"
            implementationClass = "KotlinJvmConventionPlugin"
        }
        register("focusguardAndroidLibrary") {
            id = "focusguard.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("focusguardAndroidApplication") {
            id = "focusguard.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("focusguardHilt") {
            id = "focusguard.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("focusguardCompose") {
            id = "focusguard.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("focusguardDetekt") {
            id = "focusguard.detekt"
            implementationClass = "DetektConventionPlugin"
        }
        register("focusguardKover") {
            id = "focusguard.kover"
            implementationClass = "KoverConventionPlugin"
        }
    }
}
