package com.familytree.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** The `libs` version catalog, accessible from precompiled convention plugins. */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Reads an integer version (e.g. `minSdk = "28"`) out of the catalog. */
fun VersionCatalog.int(alias: String): Int =
    findVersion(alias).orElseThrow { IllegalStateException("Version '$alias' missing from libs.versions.toml") }
        .requiredVersion.toInt()
