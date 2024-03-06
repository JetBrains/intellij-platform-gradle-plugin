// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName

// TODO currently unused
@Serializable
data class PluginsCache(
    @XmlSerialName("plugin") val plugins: List<PluginsCachePlugin>,
) {

    @Serializable
    data class PluginsCachePlugin(
        val id: String,
        val directoryName: String,
        @XmlSerialName("dependency") val dependencies: List<String> = mutableListOf(),
    )
}
