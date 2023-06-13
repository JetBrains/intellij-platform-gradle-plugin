// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.dependency

import java.io.File

class LocalIdeaDependency(
    name: String,
    version: String,
    buildNumber: String,
    classes: File,
    sources: File?,
    withKotlin: Boolean,
    builtinPluginsRegistry: BuiltinPluginsRegistry,
    extraDependencies: Collection<IdeaExtraDependency>,
) : IdeaDependency(name, version, buildNumber, classes, sources, withKotlin, builtinPluginsRegistry, extraDependencies) {

    override fun getIvyRepositoryDirectory() = when {
        version.endsWith(".SNAPSHOT") -> null
        else -> super.getIvyRepositoryDirectory()
    }
}
