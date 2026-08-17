plugins {
    alias(libs.plugins.familytree.android.library)
    alias(libs.plugins.familytree.hilt)
    alias(libs.plugins.familytree.room)
}

android {
    namespace = "com.familytree.core.database"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
