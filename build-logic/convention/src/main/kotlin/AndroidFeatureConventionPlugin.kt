import com.streambox.buildlogic.libs
import com.streambox.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature module: Android library + Compose + Hilt + type-safe navigation.
 * Features may depend on core modules only, never on another feature.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("streambox.android.library")
            pluginManager.apply("streambox.android.compose")
            pluginManager.apply("streambox.hilt")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:domain"))
                add("implementation", project(":core:model"))

                add("implementation", libs.library("androidx-compose-material3"))
                add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("androidx-navigation-compose"))
                add("implementation", libs.library("androidx-hilt-navigation-compose"))
                add("implementation", libs.library("kotlinx-serialization-json"))

                add("testImplementation", project(":core:testing"))
                add("androidTestImplementation", libs.library("androidx-compose-ui-test-junit4"))
                add("androidTestImplementation", libs.library("androidx-test-ext-junit"))
                add("androidTestImplementation", libs.library("androidx-test-runner"))
                add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))
            }
            checkNoFeatureDependencies()
        }
    }

    private fun Project.checkNoFeatureDependencies() {
        afterEvaluate {
            configurations.forEach { configuration ->
                configuration.dependencies
                    .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                    .filter { it.path.startsWith(":feature:") && it.path != path }
                    .forEach {
                        error("$path must not depend on ${it.path}: features communicate via core:domain or navigation.")
                    }
            }
        }
    }
}
