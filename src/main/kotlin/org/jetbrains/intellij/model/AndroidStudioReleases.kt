// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.model

import java.io.Serializable
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

@XmlRootElement(name = "content")
internal data class AndroidStudioReleases(

    @set:XmlAttribute
    var version: Int = 0,

    @set:XmlElement(name = "item")
    var items: List<Item> = mutableListOf(),
) : Serializable

internal data class Item(

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

    @set:XmlElement(name = "download")
    var downloads: List<Download> = mutableListOf(),
) : Serializable

internal data class Download(

    @set:XmlElement
    var link: String = "",

    @set:XmlElement
    var size: Long = 0,

    @set:XmlElement
    var checksum: String = "",
) : Serializable
