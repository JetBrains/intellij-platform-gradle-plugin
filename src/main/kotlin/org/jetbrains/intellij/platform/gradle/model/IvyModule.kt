// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.model

import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesExtension
import java.nio.file.Path
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlElementWrapper
import javax.xml.bind.annotation.XmlRootElement
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory

@XmlRootElement(name = "ivy-module")
data class IvyModule(

    @set:XmlAttribute
    var version: String = "2.0",

    @set:XmlElement
    var info: Info? = null,

    @set:XmlElement(name = "conf")
    @set:XmlElementWrapper
    var configurations: List<Configuration> = mutableListOf(),

    @set:XmlElement(name = "artifact")
    @set:XmlElementWrapper
    var publications: List<Publication> = mutableListOf(),
) {
    data class Configuration(

        @set:XmlAttribute
        var name: String? = null,

        @set:XmlAttribute
        var visibility: String? = null,
    )

    data class Info(

        @set:XmlAttribute
        var organisation: String? = null,

        @set:XmlAttribute
        var module: String? = null,

        @set:XmlAttribute
        var revision: String? = null,

        @set:XmlAttribute
        var publication: String? = null,
    )

    data class Publication(
        @set:XmlAttribute
        var name: String? = null,

        @set:XmlAttribute
        var type: String? = null,

        @set:XmlAttribute
        var ext: String? = null,

        @set:XmlAttribute
        var conf: String? = null,
    )
}

/**
 * Create a publication artifact element for Ivy XML file, like:
 *
 * ```XML
 * <ivy-module version="2.0">
 *     ...
 *     <publications>
 *         <artifact conf="default" ext="jar" name="/path/to/artifact.jar" type="jar" />
 *     </publications>
 * </ivy-module>
 * ```
 *
 * The artifact name is an actual path of the file or directory, later used to locate all entries belonging to the current local dependency.
 * Note that the path is built using [invariantSeparatorsPathString] due to path separator differences on Windows.
 *
 * In addition, the leading drive letter is always removed to start paths with `/` and to solve an issue with the Gradle file resolver.
 * If the artifact path doesn't start with `/`, the [BaseDirFileResolver] creates a malformed location with a Gradle base dir prepended,
 * like: `C:/Users/hsz/dir/C:/Users/hsz/path/to/artifact.jar`, so we make it `/Users/hsz/path/to/artifact.jar` explicitly.
 *
 * As we remove the drive letter, we later have to guess which drive the artifact belongs to by iterating over A:/, B:/, C:/, ...
 * but that's not a huge problem.
 *
 * @see [IntelliJPlatformRepositoriesExtension.ivy]
 */
internal fun Path.toPublication() = IvyModule.Publication(
    name = invariantSeparatorsPathString.replaceFirst(Regex("^[a-zA-Z]:/"), "/"),
    type = when {
        isDirectory() -> "directory"
        else -> extension
    },
    ext = when {
        isDirectory() -> null
        else -> extension
    },
    conf = "default",
)
