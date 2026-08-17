import com.familytree.buildlogic.JVM_TARGET
import com.familytree.buildlogic.configureKotlinJvm
import com.familytree.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** For Android-free modules — `core:model` and the pure-logic parts of `core:gedcom`. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        configureKotlinJvm()
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget = JVM_TARGET
            }
        }

        val catalog = libs
        dependencies {
            add("testImplementation", catalog.findLibrary("junit").get())
            add("testImplementation", catalog.findLibrary("truth").get())
            add("testImplementation", catalog.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}
