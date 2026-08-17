import androidx.room.gradle.RoomExtension
import com.familytree.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("androidx.room")
        pluginManager.apply("com.google.devtools.ksp")

        // Schema JSONs are checked into version control so migrations can be tested.
        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }

        val catalog = libs
        dependencies {
            // `api`, not `implementation`: DAOs and the database class are this module's
            // public surface, so consumers need Room's types (and `withTransaction`) too.
            add("api", catalog.findLibrary("androidx-room-runtime").get())
            add("api", catalog.findLibrary("androidx-room-ktx").get())
            add("ksp", catalog.findLibrary("androidx-room-compiler").get())
            add("androidTestImplementation", catalog.findLibrary("androidx-room-testing").get())
        }
    }
}
