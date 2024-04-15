// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.models.Coordinates

/**
 * Definition of Test Framework types available for writing tests for IntelliJ Platform plugins.
 */
interface TestFrameworkType {

    /**
     * Maven coordinates of test framework artifact;
     */
    val coordinates: Coordinates;

    enum class Platform(override val coordinates: Coordinates) : TestFrameworkType {
        JUnit4(Coordinates("com.jetbrains.intellij.platform", "test-framework")),
        JUnit5(Coordinates("com.jetbrains.intellij.platform", "test-framework-junit5")),
        Bundled(Coordinates("bundled", "lib/testFramework.jar")),
    }

    enum class Plugin(override val coordinates: Coordinates) : TestFrameworkType {
        Go(Coordinates("com.jetbrains.intellij.go", "go-test-framework")),
        Ruby(Coordinates("com.jetbrains.intellij.idea", "ruby-test-framework")),
        Java(Coordinates("com.jetbrains.intellij.java", "java-test-framework")),
        JavaScript(Coordinates("com.jetbrains.intellij.javascript", "javascript-test-framework")),
        Maven(Coordinates("com.jetbrains.intellij.maven", "maven-test-framework")),
        ReSharper(Coordinates("com.jetbrains.intellij.resharper", "resharper-test-framework")),
    }
}
