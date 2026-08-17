plugins {
    alias(libs.plugins.familytree.android.library)
    alias(libs.plugins.familytree.android.compose)
}

android {
    namespace = "com.familytree.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3.adaptive)
    api(libs.androidx.compose.material3.adaptive.layout)
    api(libs.androidx.compose.material3.adaptive.navigation)
    api(libs.androidx.compose.material3.adaptive.navigation.suite)
}
