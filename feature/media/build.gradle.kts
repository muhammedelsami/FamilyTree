plugins {
    alias(libs.plugins.familytree.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.familytree.feature.media"
}

dependencies {
    implementation(libs.androidx.exifinterface)
}
