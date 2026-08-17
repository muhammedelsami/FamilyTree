package com.familytree.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

internal val JAVA_VERSION = JavaVersion.VERSION_17
internal val JVM_TARGET = JvmTarget.JVM_17

/**
 * Shared Android + Kotlin configuration applied to every Android module,
 * whether it is the application or a library.
 *
 * Note: as of AGP 9, [CommonExtension] is no longer generic.
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    // AGP 9 exposes only getters on CommonExtension; the lambda/Action forms of
    // `defaultConfig { }` and `compileOptions { }` live on the concrete extensions.
    commonExtension.apply {
        compileSdk = libs.int("compileSdk")
        defaultConfig.minSdk = libs.int("minSdk")
        compileOptions.sourceCompatibility = JAVA_VERSION
        compileOptions.targetCompatibility = JAVA_VERSION
        compileOptions.isCoreLibraryDesugaringEnabled = true
    }

    // AGP 9 applies Kotlin itself and aligns the Kotlin jvmTarget with compileOptions,
    // so there is no separate Kotlin extension to configure here.

    // Every module gets the test dependencies from the convention plugin, and Gradle 9
    // treats "test dependencies but no tests" as a misconfiguration. For a module that
    // simply has nothing to test yet that is noise, not a problem.
    tasks.withType(Test::class.java).configureEach {
        failOnNoDiscoveredTests.set(false)
    }

    dependencies {
        add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs:2.1.5")
    }
}

/** Shared configuration for pure-JVM (Android-free) modules such as `core:model`. */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
}
