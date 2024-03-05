// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.model

import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

@XmlRootElement(name = "module")
data class ModuleDescriptor(

    @set:XmlAttribute
    var name: String = "",

    @set:XmlElement(name = "module")
    var dependencies: List<Dependency> = mutableListOf(),

    @set:XmlElement
    var resources: Resources = Resources(),
) {

    data class Dependency(
        @set:XmlAttribute
        var name: String,
    )

    data class Resources(
        @set:XmlElement(name = "resource-root")
        var resourceRoot: ResourceRoot = ResourceRoot(),
    ) {

        data class ResourceRoot(
            @set:XmlAttribute
            var path: String = "",
        )
    }
}
