package org.jetbrains.intellij.model

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "idea-plugin")
data class IdeaPlugin(
    var name: String? = null,
    var id: String? = null,
    var description: String? = null,
    @JacksonXmlProperty(localName = "change-notes")
    var changeNotes: String? = null,
    var version: String? = null,
    @JacksonXmlProperty(localName = "idea-version")
    var ideaVersion: IdeaVersion? = null,
    // TODO: Vendor type
    var vendor: String? = null,
    @JacksonXmlElementWrapper(useWrapping = false, localName = "depends")
    var depends: List<Depends> = emptyList(),
    @JacksonXmlProperty(localName = "download-url")
    var downloadUrl: String? = null,
)

data class IdeaVersion(
    @JacksonXmlProperty(localName = "since-build", isAttribute = true)
    var sinceBuild: String? = null,
    @JacksonXmlProperty(localName = "until-build", isAttribute = true)
    var untilBuild: String? = null,
)

data class Depends(
    @JacksonXmlProperty(isAttribute = true)
    var optional: Boolean? = false,
    @JacksonXmlProperty(localName = "config-file", isAttribute = true)
    var configFile: String? = null,
    var value: String? = null,
)
