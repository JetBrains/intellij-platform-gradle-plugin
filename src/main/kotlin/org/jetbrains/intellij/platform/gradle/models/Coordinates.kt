// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import org.jetbrains.intellij.platform.gradle.Constants.Locations
import java.net.URL

data class Coordinates(val groupId: String, val artifactId: String) {

    override fun toString() = "$groupId:$artifactId"
}

fun Coordinates.resolveLatestVersion(repositoryUrl: String = Locations.MAVEN_REPOSITORY): String? {
    val host = repositoryUrl.trimEnd('/')
    val path = toString().replace(':', '/').replace('.', '/')
    val url = URL("$host/$path/maven-metadata.xml")
    return decode<MavenMetadata>(url).versioning?.latest
}
