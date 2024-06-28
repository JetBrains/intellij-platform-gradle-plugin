// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

/**
 * An interface that provides access to Kotlin-specific metadata for a Gradle project.
 * Task that inherits from this interface is automatically marked as dependent on the `compileKotlin` task.
 */
interface KotlinMetadataAware {

    /**
     * Indicates that the Kotlin Gradle Plugin is loaded and available.
     */
    @get:Input
    val kotlinPluginAvailable: Property<Boolean>

    /**
     * This variable represents whether the Kotlin Coroutines library is added explicitly to the project dependencies.
     */
    @get:Input
    val kotlinxCoroutinesLibraryPresent: Property<Boolean>

    /**
     * The `apiVersion` property value of `compileKotlin.kotlinOptions` defined in the build script.
     */
    @get:Internal
    val kotlinApiVersion: Property<String?>

    /**
     * The `languageVersion` property value of `compileKotlin.kotlinOptions` defined in the build script.
     */
    @get:Internal
    val kotlinLanguageVersion: Property<String?>

    /**
     * The version of Kotlin used in the project.
     */
    @get:Internal
    val kotlinVersion: Property<String?>

    /**
     * The `jvmTarget` property value of `compileKotlin.kotlinOptions` defined in the build script.
     */
    @get:Internal
    val kotlinJvmTarget: Property<String?>

    /**
     * `kotlin.stdlib.default.dependency` property value defined in the `gradle.properties` file.
     */
    @get:Internal
    val kotlinStdlibDefaultDependency: Property<Boolean?>
}
