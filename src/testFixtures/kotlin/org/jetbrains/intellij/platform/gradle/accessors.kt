// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import java.nio.file.Path

/**
 * Resolves the path to the Gradle properties file: `gradle.properties`.
 */
val IntelliJPlatformTestBase.gradleProperties: Path
    get() = dir.resolve("gradle.properties").also(::assertExists)

/**
 * Resolves the path to the Gradle build file: `build.gradle.kts`.
 */
val IntelliJPlatformTestBase.buildFile: Path
    get() = dir.resolve("build.gradle.kts").also(::assertExists)

/**
 * Resolves the path to the Gradle settings file: `settings.gradle.kts`.
 */
val IntelliJPlatformTestBase.settingsFile: Path
    get() = dir.resolve("settings.gradle.kts").also(::assertExists)

/**
 * Resolves the path to the Gradle build directory: `build/`.
 */
val IntelliJPlatformTestBase.buildDirectory: Path
    get() = dir.resolve("build").also(::assertExists)

/**
 * Resolves the path to the IntelliJ Platform plugin descriptor file: `src/main/resources/META-INF/plugin.xml`.
 */
val IntelliJPlatformTestBase.pluginXml: Path
    get() = dir.resolve("src/main/resources/META-INF/plugin.xml").also(::assertExists)
