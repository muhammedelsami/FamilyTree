plugins {
    alias(libs.plugins.familytree.jvm.library)
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
}
