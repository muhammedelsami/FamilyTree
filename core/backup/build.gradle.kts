plugins {
    alias(libs.plugins.familytree.android.library)
    alias(libs.plugins.familytree.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.familytree.core.backup"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    // An archive is a GEDCOM export plus the tree's media, so both come along.
    implementation(project(":core:gedcom"))
    implementation(project(":core:media"))
    implementation(project(":core:database"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
