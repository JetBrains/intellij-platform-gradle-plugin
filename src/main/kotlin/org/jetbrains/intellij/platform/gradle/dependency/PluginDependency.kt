// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.dependency

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import java.io.File
import java.io.Serializable

@Suppress("BooleanMethodIsAlwaysInverted")
interface PluginDependency : Serializable {

    val id: String
    val platformPluginId: String?
    val version: String
    val channel: String?
    val artifact: File
    val jarFiles: Collection<File>
    val sourceJarFiles: Collection<File>
    val classesDirectory: File?
    val metaInfDirectory: File?
    val builtin: Boolean
    val maven: Boolean
    val notation: PluginDependencyNotation

    fun isCompatible(ideVersion: IdeVersion): Boolean

    fun getFqn(): String = "$id-$version-$formatVersion"

    companion object {
        private const val formatVersion = 3
    }
}
