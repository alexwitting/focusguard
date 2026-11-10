import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                compileSdk = 36
                defaultConfig {
                    minSdk = 26
                    targetSdk = 35
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                // One debug key for everyone: a CI-built debug APK and one built on any machine
                // used to be signed with different auto-generated keys, so installing one over the
                // other failed with INSTALL_FAILED_UPDATE_INCOMPATIBLE. The key is debug-only and
                // uses Android's standard public debug passwords — it is not a secret.
                signingConfigs.getByName("debug") {
                    storeFile = rootProject.file("config/signing/debug.keystore")
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_21
                    targetCompatibility = JavaVersion.VERSION_21
                }
            }

            configureKotlinJvmAndTests()
        }
    }
}
