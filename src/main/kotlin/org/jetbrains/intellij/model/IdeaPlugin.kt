package org.jetbrains.intellij.model

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "idea-plugin")
data class IdeaPlugin(
    var name: String? = null,
    var id: String? = null,
    var vendor: String? = null,
    var version: String? = null,
    @JacksonXmlProperty(localName = "download-url")
    var downloadUrl: String? = null,
)
