plugins {
    alias(libs.plugins.familytree.android.library)
    alias(libs.plugins.familytree.hilt)
}

android {
    namespace = "com.familytree.core.datastore"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.datastore.preferences)
}
