import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Shared by every Kotlin-compiling convention plugin (JVM, Android library, Android application):
 * pins the compiler's JVM target, wires JUnit5 as the test platform with a heap ceiling, and adds
 * the JUnit5 BOM + launcher every test source set needs.
 */
internal fun Project.configureKotlinJvmAndTests() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Explicit ceiling instead of the JVM's default heap-sizing heuristic, which
        // is computed from memory available at process launch and is unreliable on a
        // host where other apps are already competing for RAM.
        maxHeapSize = "1g"
    }

    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies {
        "testImplementation"(platform(libs.findLibrary("junit5-bom").get()))
        "testRuntimeOnly"(libs.findLibrary("junit5-launcher").get())
    }
}
