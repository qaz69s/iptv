import com.streambox.buildlogic.library
import com.streambox.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Robolectric for the JVM unit tests of one Android library (P3a: Room DAO and migration tests).
 *
 * Robolectric only ships a JUnit 4 runner, and the JUnit Platform's Vintage engine is deprecated
 * since JUnit 6. So a module that applies this plugin runs its unit tests on plain JUnit 4
 * (`useJUnit()`) and writes them as JUnit 4 tests; every other module keeps JUnit 5 (Jupiter).
 * Apply after `streambox.android.library`.
 */
class AndroidRobolectricConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            tasks.withType<Test>().configureEach {
                useJUnit()
                // Robolectric loads a full android-all jar per SDK level.
                maxHeapSize = "2g"
            }
            dependencies {
                add("testImplementation", libs.library("junit4"))
                add("testImplementation", libs.library("robolectric"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
            }
        }
    }
}
