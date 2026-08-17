import com.android.build.api.dsl.LibraryExtension
import com.familytree.buildlogic.configureKotlinAndroid
import com.familytree.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 has built-in Kotlin support; applying `org.jetbrains.kotlin.android` is an error.
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            testOptions.unitTests.isIncludeAndroidResources = true
        }

        val catalog = libs
        dependencies {
            add("implementation", catalog.findLibrary("kotlinx-coroutines-android").get())
            add("testImplementation", catalog.findLibrary("junit").get())
            add("testImplementation", catalog.findLibrary("truth").get())
            add("testImplementation", catalog.findLibrary("kotlinx-coroutines-test").get())
            add("testImplementation", catalog.findLibrary("turbine").get())
            add("androidTestImplementation", catalog.findLibrary("androidx-junit").get())
        }
    }
}
