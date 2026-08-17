import com.familytree.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Everything a `feature:*` module needs: Android library + Compose + Hilt + navigation,
 * plus the design system and the domain layer it renders.
 *
 * Feature modules deliberately do NOT see `core:data` — Hilt binds implementations in `:app`.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("familytree.android.library")
        pluginManager.apply("familytree.android.compose")
        pluginManager.apply("familytree.hilt")

        val catalog = libs
        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:ui"))

            add("implementation", catalog.findLibrary("androidx-hilt-navigation-compose").get())
            add("implementation", catalog.findLibrary("androidx-navigation-compose").get())
            add("implementation", catalog.findLibrary("kotlinx-serialization-json").get())
            add("implementation", catalog.findLibrary("kotlinx-datetime").get())
        }
    }
}
