plugins {
    alias(libs.plugins.familytree.android.library)
    alias(libs.plugins.familytree.android.compose)
}

android {
    namespace = "com.familytree.core.ui"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:designsystem"))
    // Every screen that shows a photo needs the resolver, so it travels with the
    // shared composables rather than being re-provided by each feature.
    api(project(":core:media"))
    implementation(project(":core:common"))

    implementation(libs.coil.compose)
    implementation(libs.coil.video)
}
