plugins {
    alias(libs.plugins.familytree.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.familytree.feature.diagram"
}

dependencies {
    implementation(project(":core:diagram"))
}
