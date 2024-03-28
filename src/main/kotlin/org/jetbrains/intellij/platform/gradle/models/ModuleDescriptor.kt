// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class ModuleDescriptor(
    val name: String,
    @XmlElement @XmlChildrenName("module") val dependencies: List<Dependency>,
    @XmlElement @XmlSerialName("resources") val resources: Resources?,
) {

    @Serializable
    data class Dependency(
        val name: String,
    )

    @Serializable
    data class Resources(
        @XmlElement @XmlSerialName("resource-root") val resourceRoot: ResourceRoot,
    ) {

        @Serializable
        data class ResourceRoot(
            val path: String,
        )
    }
}
