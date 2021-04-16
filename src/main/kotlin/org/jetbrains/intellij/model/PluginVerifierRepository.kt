package org.jetbrains.intellij.model

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "metadata")
data class PluginVerifierRepository(
    var groupId: String? = null,
    var artifactId: String? = null,
    var versioning: Versioning? = null,
)

data class Versioning(
    var latest: String? = null,
    var release: String? = null,
    var lastUpdated: String? = null,
    var versions: List<String>? = emptyList(),
)
