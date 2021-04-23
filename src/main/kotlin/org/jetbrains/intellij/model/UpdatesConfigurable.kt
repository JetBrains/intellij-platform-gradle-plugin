package org.jetbrains.intellij.model

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "application")
data class UpdatesConfigurable(
    @JacksonXmlProperty(localName = "component")
    @JacksonXmlElementWrapper(useWrapping = false)
    var items: MutableList<Component> = mutableListOf(),
)

data class Component(
    @JacksonXmlProperty(localName = "option")
    @JacksonXmlElementWrapper(useWrapping = false)
    var options: MutableList<Option> = mutableListOf(),
    @JacksonXmlProperty(isAttribute = true)
    var name: String,
)

data class Option(
    @JacksonXmlProperty(isAttribute = true)
    var name: String,
    @JacksonXmlProperty(isAttribute = true)
    var value: Boolean,
)
