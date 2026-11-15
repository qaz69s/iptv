import com.streambox.buildlogic.configureKotlinCompiler
import com.streambox.buildlogic.configureUnitTests
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure

/** Pure Kotlin/JVM module (no Android dependency), e.g. parsers and domain logic. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            configureKotlinCompiler()
            configureUnitTests()
        }
    }
}
