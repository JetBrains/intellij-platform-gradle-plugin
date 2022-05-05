// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import org.gradle.api.Project
import java.io.File

interface PluginsRepository {

    fun resolve(project: Project, plugin: PluginDependencyNotation, context: String?): File?

    fun postResolve(project: Project, context: String?)
}
