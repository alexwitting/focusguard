import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Hilt/Dagger's generated wiring (per-class `_Factory`, `_HiltModules`, `_MembersInjector`,
 * `Hilt_*` base classes) lands in the same package as the hand-written class it's generated
 * for, so every module's own Kover report needs the same exclusions the root aggregate report
 * uses — otherwise per-module coverage numbers are inflated with boilerplate nobody writes.
 *
 * Compose screens, Activities, and the Application class need instrumented UI tests (Espresso +
 * Compose semantics, a different tool than JUnit5/MockK) to exercise meaningfully — they're
 * excluded here so the reported percentage reflects the business logic (ViewModels, UseCases,
 * Repositories, Engines) that unit tests can actually cover. DI `@Module` classes are pure Hilt
 * `@Provides` wiring with no branches, same rationale as the Hilt-codegen exclusion above.
 * Room's `*_Impl` DAO/database classes are KSP-generated SQL execution code, exercisable only
 * against a real (or in-memory) SQLite database — i.e. an instrumented test, not a JVM unit
 * test with a mocked DAO.
 *
 * Platform Service subclasses (AccessibilityService/VpnService) are NOT excluded — core:system
 * pulls in Robolectric (via the community JUnit5 bridge, since Robolectric itself has no
 * official JUnit5 support) specifically to unit-test FocusGuardAccessibilityService and
 * DnsVpnService with a simulated Android framework.
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
                                "*Activity*",
                                "*Application*",
                                "*Module",
                                "*Module\$*",
                                "FocusGuardDatabase",
                                // Private UI-display data holders co-located inside Compose
                                // Screen files (mapping a domain enum to icon/title/copy text) —
                                // same non-unit-testable category as the screens themselves.
                                // Add future look-alikes here rather than excluding the whole file.
                                "StepMeta",
                                "StrategyOption",
                                "FilterInfo",
                            )
                            annotatedBy("androidx.compose.runtime.Composable")
                        }
                    }
                }
            }
        }
    }
}
