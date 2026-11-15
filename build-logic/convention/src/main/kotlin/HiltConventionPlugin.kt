import com.streambox.buildlogic.libs
import com.streambox.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Hilt with KSP. On pure JVM modules only the Dagger/Hilt core artifacts are added. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            dependencies {
                add("ksp", libs.library("hilt-compiler"))
            }
            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                dependencies { add("implementation", libs.library("hilt-core")) }
            }
            pluginManager.withPlugin("com.android.base") {
                pluginManager.apply("com.google.dagger.hilt.android")
                dependencies { add("implementation", libs.library("hilt-android")) }
            }
        }
    }
}
