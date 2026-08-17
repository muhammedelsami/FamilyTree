import com.android.build.api.dsl.CommonExtension
import com.familytree.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Enables Compose on any Android module (application or library) and wires the
 * BOM-managed Compose artifacts every UI module needs.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.getByType<CommonExtension>().buildFeatures.compose = true

        val catalog = libs
        val bom = catalog.findLibrary("androidx-compose-bom").get()
        dependencies {
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))

            add("implementation", catalog.findLibrary("androidx-compose-ui").get())
            add("implementation", catalog.findLibrary("androidx-compose-ui-graphics").get())
            add("implementation", catalog.findLibrary("androidx-compose-ui-tooling-preview").get())
            add("implementation", catalog.findLibrary("androidx-compose-material3").get())
            add("implementation", catalog.findLibrary("androidx-compose-material-icons-extended").get())
            add("implementation", catalog.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("implementation", catalog.findLibrary("androidx-lifecycle-viewmodel-compose").get())

            add("debugImplementation", catalog.findLibrary("androidx-compose-ui-tooling").get())
            add("debugImplementation", catalog.findLibrary("androidx-compose-ui-test-manifest").get())
            add("androidTestImplementation", catalog.findLibrary("androidx-compose-ui-test-junit4").get())
        }
    }
}
