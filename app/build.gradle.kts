import java.util.Properties

plugins {
    alias(libs.plugins.familytree.android.application)
    alias(libs.plugins.familytree.android.compose)
    alias(libs.plugins.familytree.hilt)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Upload-key credentials, when this machine has them.
 *
 * The keystore and its passwords are the only secret this repository has, so they live in
 * a gitignored `keystore.properties` at the root rather than in this file. A checkout
 * without it still builds every variant — the release APK simply comes out unsigned,
 * which is what anyone reading the source needs and no more.
 *
 * Read through `providers.fileContents` rather than `File.exists()`, because the
 * configuration cache is on: a plain filesystem check is not a declared input, so the
 * first build after dropping the file in reuses the cached configuration and quietly
 * produces an *unsigned* release. This provider is an input, and appearing invalidates it.
 */
val keystoreProperties = providers
    .fileContents(layout.projectDirectory.file("../keystore.properties"))
    .asText
    .map { text -> Properties().apply { load(text.reader()) } }
    .orNull

/**
 * Firebase — Crashlytics and push — when this checkout has the project's config.
 *
 * `google-services.json` is gitignored because it carries the project's API key and this
 * repository is public; CI writes it from a secret. Without it the Google Services plugin
 * fails the build outright, so both Firebase plugins are applied only when it is there: a
 * fresh clone still builds, and the app simply runs without crash reports or push. Read
 * through `providers.fileContents` for the same configuration-cache reason as the keystore.
 */
val hasFirebaseConfig = providers
    .fileContents(layout.projectDirectory.file("google-services.json"))
    .asText
    .isPresent
if (hasFirebaseConfig) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

android {
    namespace = "com.familytrees.app"

    defaultConfig {
        applicationId = "com.familytrees.app"
        // CI passes -PversionCode: one above the highest build Play already has, so every
        // push produces an upload Play accepts without anyone editing this file. The
        // literal is what a local build gets.
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(3)
        // Major and minor are chosen by hand; the patch is the build number, so each CI
        // upload reads 1.0.4, 1.0.5, … in Play Console and on the device.
        versionName = "1.0.$versionCode"
        resourceConfigurations += setOf("en", "tr", "ar")
    }

    signingConfigs {
        keystoreProperties?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isPseudoLocalesEnabled = true
            // Crashes on a development build are the developer's to see in Logcat; sent to
            // Crashlytics they would bury the ones real users hit.
            manifestPlaceholders["crashlyticsCollectionEnabled"] = false
        }
        release {
            manifestPlaceholders["crashlyticsCollectionEnabled"] = true
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Null on a checkout with no credentials, which leaves the build unsigned
            // rather than failing — see keystoreProperties above.
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:gedcom"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))

    implementation(project(":feature:trees"))
    implementation(project(":feature:person"))
    implementation(project(":feature:family"))
    implementation(project(":feature:diagram"))
    implementation(project(":feature:media"))
    implementation(project(":feature:backup"))
    implementation(project(":feature:share"))
    implementation(project(":feature:settings"))
    implementation(project(":core:backup"))
    implementation(project(":core:notifications"))
    implementation(project(":core:billing"))

    implementation(libs.androidx.core.ktx)
    // The manifest names AppLocalesMetadataHolderService, so the class has to be here.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    androidTestImplementation(libs.androidx.espresso.core)
}
