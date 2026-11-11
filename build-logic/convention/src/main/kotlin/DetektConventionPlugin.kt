import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.gitlab.arturbosch.detekt")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig = true
                parallel = true
                config.setFrom(files("${rootProject.rootDir}/config/detekt/detekt.yml"))
                // One baseline file per module.
                val modulePathSegment = path.removePrefix(":").replace(":", "-")
                baseline = file("${rootProject.rootDir}/config/detekt/baseline-$modulePathSegment.xml")
                autoCorrect = false
            }

            extensions.configure<KtlintExtension> {
                android.set(true)
            }
        }
    }
}
