// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import java.io.File
import java.io.Serializable

@Suppress("BooleanMethodIsAlwaysInverted")
interface PluginDependency : Serializable {
    companion object {
        private const val formatVersion = 1
    }

    val id: String

    val version: String

    val channel: String?

    val artifact: File

    val jarFiles: Collection<File>

    val classesDirectory: File?

    val metaInfDirectory: File?

    val sourcesDirectory: File?

    val builtin: Boolean

    val maven: Boolean

    val notation: PluginDependencyNotation

    fun isCompatible(ideVersion: IdeVersion): Boolean

    fun getFqn(): String = "$id-$version-$formatVersion"
}
