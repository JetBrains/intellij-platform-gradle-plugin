package org.jetbrains.intellij.model

import java.io.Serializable
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

@XmlRootElement(name = "content")
data class AndroidStudioReleases(

    @set:XmlAttribute
    var version: Int = 0,

    @set:XmlElement(name = "item")
    var items: List<Item> = mutableListOf(),
) : Serializable

data class Item(

    @set:XmlElement
    var name: String = "",

    @set:XmlElement
    var build: String = "",

    @set:XmlElement
    var version: String = "",

    @set:XmlElement
    var channel: String = "",

    @set:XmlElement
    var platformBuild: String? = null,

    @set:XmlElement
    var platformVersion: String? = null,

    @set:XmlElement
    var date: String = "",
) : Serializable
