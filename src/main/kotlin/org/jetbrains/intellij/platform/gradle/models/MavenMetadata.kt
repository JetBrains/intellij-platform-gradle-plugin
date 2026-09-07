// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class MavenMetadata(
    @XmlElement val groupId: String,
    @XmlElement val artifactId: String,
    @XmlElement val version: String?,
    @XmlElement(false) val modelVersion: String?,
    @XmlElement @XmlSerialName("versioning") val versioning: MavenMetadataVersioning?,
) {

    @Serializable
    data class MavenMetadataVersioning(
        @XmlElement val latest: String,
        @XmlElement val release: String?,
        @XmlElement val lastUpdated: String?,
        @XmlChildrenName("version") val versions: List<String>,
    )
}
