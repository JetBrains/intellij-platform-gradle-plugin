// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

sealed class DependencyVersion {
    /**
     * Represents the selection strategy where the closest version of a dependency is chosen.
     * The closest version (exact or the first lower) to the current IntelliJ Platform version is selected.
     *
     * This version resolving is expensive as Gradle requests all Maven repositories added to the project
     * to resolve all available dependency versions by obtaining its `maven-metadata.xml` file, like:
     * https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/platform/test-framework/maven-metadata.xml
     */
    object Closest: DependencyVersion()

    /**
     * Reflects the currently used IntelliJ Platform build number.
     */
    object IntelliJPlatform: DependencyVersion()

    /**
     * Represents the selection strategy where the latest available version of a dependency is chosen.
     *
     * This version resolving is expensive as Gradle requests all Maven repositories added to the project
     * to resolve all available dependency versions by obtaining its `maven-metadata.xml` file, like:
     * https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/platform/test-framework/maven-metadata.xml
     */
    object Latest: DependencyVersion()

    class Exact(val version: String): DependencyVersion()
}
