import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Coverage exclusions shared by every module's report and the root aggregate:
 * - Hilt/Dagger and Room generated code, and DI `@Module` classes;
 * - Compose UI (screens, dialogs, sheets, `@Composable` functions), Activities and the
 *   Application, which need instrumented UI tests;
 * - UI-only data holders listed below.
 * Accessibility and VPN services are still measured; they have Robolectric tests.
 */
class KoverConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            extensions.configure<KoverProjectExtension> {
                reports {
                    filters {
                        excludes {
                            packages("dagger.hilt.internal.aggregatedroot.codegen", "hilt_aggregated_deps")
                            classes(
                                "*Factory",
                                "*Factory\$*",
                                "*_MembersInjector",
                                "*_HiltModules*",
                                "*.Hilt_*",
                                "*_GeneratedInjector",
                                "*_Impl",
                                "*_Impl\$*",
                                "*ScreenKt*",
                                // Compose UI files; put testable helpers in a separate file.
                                "*DialogKt",
                                "*DialogKt\$*",
                                "*SheetKt",
                                "*SheetKt\$*",
                                "*Activity*",
                                "*Application*",
                                "*Module",
                                "*Module\$*",
                                "FocusGuardDatabase",
                                // UI-only data holders inside Compose files. Patterns match
                                // fully-qualified names, so they need the "*." prefix.
                                "*.StepMeta",
                                "*.StepMeta\$*",
                                "*.StrategyOption",
                                "*.StrategyOption\$*",
                                "*.FilterInfo",
                                "*.FilterInfo\$*",
                                "*.RuleDetail",
                                "*.RuleDetail\$*",
                                "*.HealthItem",
                                "*.HealthItem\$*",
                                "*.BlockTarget",
                                "*.BlockTarget\$*",
                            )
                            annotatedBy("androidx.compose.runtime.Composable")
                        }
                    }
                }
            }
        }
    }
}
