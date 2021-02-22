package org.jetbrains.intellij.dependency

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
