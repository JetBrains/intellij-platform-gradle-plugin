// Copyright 2000-2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

/**
 * Represents the product-modules.xml structure that defines module inclusions and bundled plugins.
 *
 * Example:
 * ```xml
 * <product-modules>
 *   <include>
 *     <from-module>intellij.platform.frontend.split</from-module>
 *     <from-module>intellij.platform.frontend.split</from-module>
 *   </include>
 *   <bundled-plugins>
 *     <module>intellij.idea.frontend.split.customization</module>
 *     <module>intellij.java.frontend</module>
 *     <module>kotlin.frontend</module>
 *     <module>intellij.keymap.eclipse</module>
 *     <module>intellij.keymap.netbeans</module>
 *     <module>intellij.keymap.visualStudio</module>
 *   </bundled-plugins>
 * </product-modules>
 * ```
 */
/**
 * Represents the product-modules.xml structure that defines module inclusions and bundled plugins.
 */
@Serializable
@XmlSerialName("product-modules")
data class ProductModules(
    @XmlElement val include: ModuleIncludes? = null,
    @XmlElement @XmlSerialName("bundled-plugins") val bundledPlugins: BundledPlugins? = null,
) {

    /**
     * Represents the 'include' section containing modules to be included.
     */
    @Serializable
    @XmlSerialName("include")
    data class ModuleIncludes(
        @XmlElement val fromModules: List<FromModule> = emptyList(),
    ) {
        /**
         * Represents a from-module element
         */
        @Serializable
        @XmlSerialName("from-module")
        data class FromModule(
            @XmlValue val value: String
        )
    }

    /**
     * Represents the 'bundled-plugins' section containing plugin modules.
     */
    @Serializable
    @XmlSerialName("bundled-plugins")
    data class BundledPlugins(
        @XmlElement val modules: List<Module> = emptyList(),
    ) {
        /**
         * Represents a module element
         */
        @Serializable
        @XmlSerialName("module")
        data class Module(
            @XmlValue val value: String
        )
    }
}
