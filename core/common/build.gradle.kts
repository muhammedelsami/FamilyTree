plugins {
    alias(libs.plugins.familytree.android.library)
    alias(libs.plugins.familytree.hilt)
}

android {
    namespace = "com.familytree.core.common"
}

dependencies {
    api(project(":core:model"))

    // Purely for AppCompatDelegate's per-app language backport below Android 13 — see
    // AppLocales. Kept as `implementation` so no AppCompat type reaches the feature
    // modules that depend on this one.
    implementation(libs.androidx.appcompat)
}
