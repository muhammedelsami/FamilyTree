plugins {
    alias(libs.plugins.familytree.android.library)
    alias(libs.plugins.familytree.hilt)
}

android {
    namespace = "com.familytree.core.data"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    // The sync cycle runs as a background job, so the scheduler lives with the engine.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    api(project(":core:database"))
    implementation(project(":core:datastore"))

    // Repository tests run against the real Room database under Robolectric.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
