// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.utils.Version

/**
 * Definition of Test Framework types available for writing tests for IntelliJ Platform plugins.
 *
 * @param coordinates Maven coordinates of test framework artifact.
 */
@Suppress("unused")
sealed class TestFrameworkType(vararg val coordinates: Coordinates) {
    internal fun coordinatesFor(buildNumber: Version) = coordinates.filterNot {
        it.artifactId.startsWith(IDE_STARTER_PRODUCT_PREFIX) &&
                buildNumber < Constraints.PRODUCT_STARTER_MINIMAL_BUILD_NUMBER
    }

    object Platform : TestFrameworkType(Coordinates("com.jetbrains.intellij.platform", "test-framework"))
    object JUnit5 : TestFrameworkType(Coordinates("com.jetbrains.intellij.platform", "test-framework-junit5"))
    object Bundled : TestFrameworkType(Coordinates("bundled", "lib/testFramework.jar"))
    object Metrics : TestFrameworkType(
        Coordinates("com.jetbrains.intellij.tools", "ide-metrics-benchmark"),
        Coordinates("com.jetbrains.intellij.tools", "ide-metrics-collector"),
        Coordinates("com.jetbrains.intellij.tools", "ide-util-common"),
    )
    object Starter : TestFrameworkType(
        Coordinates("com.jetbrains.intellij.tools","ide-starter-squashed"),
        Coordinates("com.jetbrains.intellij.tools","ide-starter-junit5"),
        Coordinates("com.jetbrains.intellij.tools","ide-starter-driver"),
        Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-idea-ultimate"),
        Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-idea-community"),
        Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-pycharm"),
        Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-pycharm-community"),
        Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-goland"),
        Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-rider"),
        Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-webstorm"),
        Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-phpstorm"),
        Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-rubymine"),
        Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-clion"),
        Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-datagrip"),
        Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-rustrover"),
        Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-android-studio"),
        Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-gateway"),
        Coordinates("com.jetbrains.intellij.driver","driver-client"),
        Coordinates("com.jetbrains.intellij.driver","driver-sdk"),
        Coordinates("com.jetbrains.intellij.driver","driver-model"),
    )

    /**
     * Different utility methods for tests that use the UI Driver framework
     * The APIs in these modules may be unstable and subject to change without notice.
     */
    object UiUtil {
        object Debugger : TestFrameworkType(Coordinates("com.jetbrains.intellij.debugger", "debugger-ui-test-util"))
        object Jupyter : TestFrameworkType(Coordinates("com.jetbrains.intellij.jupyter", "jupyter-ui-test-util"))
    }

    object Plugin {
        object CLion : TestFrameworkType(Coordinates("com.jetbrains.intellij.clion", "clion-merged-test-framework"))
        object CSS : TestFrameworkType(Coordinates("com.jetbrains.intellij.css", "css-test-framework"))
        object Debugger : TestFrameworkType(Coordinates("com.jetbrains.intellij.platform", "debugger-test-framework"))
        object ExternalSystem : TestFrameworkType(Coordinates("com.jetbrains.intellij.platform", "external-system-test-framework"))
        object Go : TestFrameworkType(Coordinates("com.jetbrains.intellij.go", "go-test-framework"))
        object IJent : TestFrameworkType(Coordinates("com.jetbrains.intellij.platform", "ijent-test-framework"))
        object Ruby : TestFrameworkType(Coordinates("com.jetbrains.intellij.idea", "ruby-test-framework"))
        object Java : TestFrameworkType(Coordinates("com.jetbrains.intellij.java", "java-test-framework"))
        object JavaScript : TestFrameworkType(Coordinates("com.jetbrains.intellij.javascript", "javascript-test-framework"))
        object Jupyter : TestFrameworkType(Coordinates("com.jetbrains.intellij.jupyter", "jupyter-test-framework"))
        object Kotlin : TestFrameworkType(Coordinates("com.jetbrains.intellij.kotlin", "kotlin-base-test-framework"))
        object LSP : TestFrameworkType(Coordinates("com.jetbrains.intellij.platform", "lsp-test-framework"))
        object Maven : TestFrameworkType(Coordinates("com.jetbrains.intellij.maven", "maven-test-framework"))
        object MLCompletion : TestFrameworkType(Coordinates("com.jetbrains.intellij.ml", "ml-llm-completion-test-framework"))
        object MLCompletionCloud : TestFrameworkType(Coordinates("com.jetbrains.intellij.ml", "ml-llm-completion-cloud-test-framework"))
        object MLNextEdits : TestFrameworkType(Coordinates("com.jetbrains.intellij.ml", "ml-llm-next-edits-test-framework"))
        object NavBar : TestFrameworkType(Coordinates("com.jetbrains.intellij.platform", "navbar-test-framework"))
        object Notebooks : TestFrameworkType(Coordinates("com.jetbrains.intellij.notebooks", "notebooks-visualization-test-framework"))
        object PolySymbols : TestFrameworkType(Coordinates("com.jetbrains.intellij.platform", "poly-symbols-test-framework"))
        object Python : TestFrameworkType(Coordinates("com.jetbrains.intellij.python", "python-community-test-framework"))
        object Qodana : TestFrameworkType(Coordinates("com.jetbrains.intellij.qodana", "qodana-test-framework"))
        object RDClient : TestFrameworkType(Coordinates("com.jetbrains.intellij.rd", "rd-client-test-framework"))
        object ReSharper : TestFrameworkType(Coordinates("com.jetbrains.intellij.resharper", "resharper-test-framework"))
        object Rider : TestFrameworkType(Coordinates("com.jetbrains.intellij.rider", "rider-test-framework"))
        object Statistics : TestFrameworkType(Coordinates("com.jetbrains.intellij.platform", "statistics-test-framework"))
        object UAST : TestFrameworkType(Coordinates("com.jetbrains.intellij.platform", "uast-test-framework"))
        object VCS : TestFrameworkType(Coordinates("com.jetbrains.intellij.platform", "vcs-test-framework"))
        object XML : TestFrameworkType(Coordinates("com.jetbrains.intellij.xml", "xml-test-framework"))
        object WebSymbols : TestFrameworkType(Coordinates("com.jetbrains.intellij.platform", "web-symbols-test-framework"))
    }

    private companion object {
        const val IDE_STARTER_PRODUCT_PREFIX = "ide-starter-product-"
    }
}
