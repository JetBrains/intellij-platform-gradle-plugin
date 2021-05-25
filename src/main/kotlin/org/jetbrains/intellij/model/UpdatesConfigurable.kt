package org.jetbrains.intellij.model

import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

@XmlRootElement(name = "application")
data class UpdatesConfigurable(

    @set:XmlElement(name = "component")
    var components: List<UpdatesConfigurableComponent> = mutableListOf(),
)

data class UpdatesConfigurableComponent(

    @set:XmlAttribute
    var name: String? = null,

    @set:XmlElement(name = "option")
    var options: List<UpdatesConfigurableOption> = mutableListOf(),
)

data class UpdatesConfigurableOption(

    @set:XmlAttribute
    var name: String? = null,

    @set:XmlAttribute
    var value: Boolean? = null,
)
