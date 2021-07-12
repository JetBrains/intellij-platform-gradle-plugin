package org.jetbrains.intellij.dependency

import java.io.File

class JpsIdeaDependency(
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
