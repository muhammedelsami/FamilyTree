plugins {
    alias(libs.plugins.familytree.android.library)
    alias(libs.plugins.familytree.hilt)
}

android {
    namespace = "com.familytree.core.gedcom"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    api(project(":core:database"))

    // The FamilySearch GEDCOM model, used purely as an interop format: it parses and
    // writes .ged files and feeds the diagram layout engine. Room remains the app's
    // own persistence model. `api` because GedcomProjector returns this type.
    api(libs.gedcom)

    // Room needs a Context, so the round-trip test runs the real database under
    // Robolectric rather than on a device — it stays a fast, CI-friendly unit test.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
