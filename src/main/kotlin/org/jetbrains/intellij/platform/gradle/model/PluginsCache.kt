// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.model

import java.io.Serializable
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlElementWrapper
import javax.xml.bind.annotation.XmlRootElement

@XmlRootElement(name = "plugins")
data class PluginsCache(

    @set:XmlElement(name = "plugin")
    var plugins: List<PluginsCachePlugin> = mutableListOf(),
) : Serializable

data class PluginsCachePlugin(

    @set:XmlAttribute
    var id: String = "",

    @set:XmlAttribute
    var directoryName: String = "",

    @set:XmlElement(name = "dependency")
    @set:XmlElementWrapper
    var dependencies: List<String> = mutableListOf(),
) : Serializable
