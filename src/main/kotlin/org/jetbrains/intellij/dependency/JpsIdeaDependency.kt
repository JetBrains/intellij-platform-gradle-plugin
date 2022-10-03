// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import java.io.File

internal class JpsIdeaDependency(
    version: String,
    buildNumber: String,
    classes: File,
    sources: File?,
    withKotlin: Boolean,
    context: String?,
) : IdeaDependency(
    "ideaJPS",
    version,
    buildNumber,
    classes,
    sources,
    withKotlin,
    BuiltinPluginsRegistry(classes, context),
    emptyList(),
) {

    private val allowedJarNames = listOf("jps-builders.jar", "jps-model.jar", "util.jar")

    override fun collectJarFiles() = super.collectJarFiles().filter { allowedJarNames.contains(it.name) }
}
