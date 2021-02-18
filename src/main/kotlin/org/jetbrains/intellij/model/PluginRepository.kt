package org.jetbrains.intellij.model

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "plugin-repository")
data class PluginRepository(
    @JacksonXmlProperty(localName = "category")
    @JacksonXmlElementWrapper(useWrapping = false)
    var categories: List<Category> = emptyList(),
)

data class Category(
    @JacksonXmlProperty(isAttribute = true)
    var name: String,

    @JacksonXmlProperty(localName = "idea-plugin")
    @JacksonXmlElementWrapper(useWrapping = false)
    var plugins: List<IdeaPlugin> = emptyList(),
)
