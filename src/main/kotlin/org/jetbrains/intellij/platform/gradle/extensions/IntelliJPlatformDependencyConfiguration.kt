// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.property
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.ProductMode
import javax.inject.Inject

/**
 * Configuration class for IntelliJ Platform dependencies using DSL syntax.
 * Supports assignment of both direct values and Provider instances.
 */
abstract class IntelliJPlatformDependencyConfiguration @Inject constructor(objects: ObjectFactory) {

    /**
     * The type of the IntelliJ Platform dependency.
     */
    val type = objects.property<IntelliJPlatformType>()

    /**
     * The version of the IntelliJ Platform dependency.
     */
    val version = objects.property<String>()

    /**
     * Switches between the IDE installer and archive from the IntelliJ Maven repository.
     */
    val useInstaller = objects.property<Boolean>().convention(true)

    /**
     * Describes a mode in which a product may be started.
     */
    val productMode = objects.property<ProductMode>().convention(ProductMode.MONOLITH)

    /**
     * The name of the configuration to add the dependency to.
     */
    val configurationName = objects.property<String>()

    /**
     * The name of the IntelliJ Platform consumer configuration which holds information about the current IntelliJ Platform instance.
     */
    internal val intellijPlatformConfigurationName = objects.property<String>()
}
