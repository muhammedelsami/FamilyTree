plugins {
    alias(libs.plugins.familytree.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.familytree.feature.settings"
}

dependencies {
    // The screen changes the reminder time, so it schedules the job as well as storing it.
    implementation(project(":core:notifications"))
}
