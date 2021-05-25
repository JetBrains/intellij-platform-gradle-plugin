package org.jetbrains.intellij.model

import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlElementWrapper
import javax.xml.bind.annotation.XmlRootElement

@XmlRootElement(name = "metadata")
data class PluginVerifierRepository(

    @set:XmlElement
    var groupId: String? = null,

    @set:XmlElement
    var artifactId: String? = null,

    @set:XmlElement
    var versioning: PluginVerifierRepositoryVersioning? = null,
)

data class PluginVerifierRepositoryVersioning(

    @set:XmlElement
    var latest: String? = null,

    @set:XmlElement
    var release: String? = null,

    @set:XmlElement
    var lastUpdated: String? = null,

    @set:XmlElement(name = "version")
    @set:XmlElementWrapper
    var versions: List<String>? = mutableListOf(),
)
