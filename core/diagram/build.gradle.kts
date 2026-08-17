plugins {
    alias(libs.plugins.familytree.android.library)
    alias(libs.plugins.familytree.android.compose)
    alias(libs.plugins.familytree.hilt)
}

android {
    namespace = "com.familytree.core.diagram"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    // The engine works on the FamilySearch model, so the projector comes with it.
    api(project(":core:gedcom"))

    // Family Gem's diagram layout engine. Pure Java, no Android dependencies: it takes a
    // GEDCOM model and returns coordinates in dp. Distributed under GPL v3, which is why
    // this whole application is GPL — see NOTICE.md.
    implementation(files("libs/gedcomgraph-3.12.jar"))
    implementation(libs.gedcom)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
