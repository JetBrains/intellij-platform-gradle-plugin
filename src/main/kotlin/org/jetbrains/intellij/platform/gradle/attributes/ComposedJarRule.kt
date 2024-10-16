// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.attributes

import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.LibraryElements
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes

/**
 * See:
 * - [Attribute Matching](https://docs.gradle.org/current/userguide/variant_attributes.html#sec:attribute_matching)
 * - [Variant-aware sharing of artifacts between projects](https://docs.gradle.org/current/userguide/cross_project_publications.html#sec:variant-aware-sharing)
 */
abstract class ComposedJarRule : AttributeCompatibilityRule<LibraryElements> {

    override fun execute(details: CompatibilityCheckDetails<LibraryElements>) = details.run {
        if (consumerValue?.name == Attributes.COMPOSED_JAR_NAME && producerValue?.name == LibraryElements.JAR) {
            compatible()
        }
    }
}
