plugins {
    alias(libs.plugins.familytree.android.library)
    alias(libs.plugins.familytree.hilt)
}

android {
    namespace = "com.familytree.core.media"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))

    // Media folders are stored as persisted SAF tree URIs, which are only walkable
    // through DocumentFile.
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
