// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.artifacts.transform

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ZIP_TYPE
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradleplugin.asPath
import org.jetbrains.intellij.platform.gradleplugin.collectIntelliJPlatformDependencyJars
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

@DisableCachingByDefault(because = "Not worth caching")
abstract class IntelliJPlatformCollectorTransformer : TransformAction<IntelliJPlatformCollectorTransformer.Parameters> {

    interface Parameters : TransformParameters {

        @get:CompileClasspath
        val sourcesClasspath: ConfigurableFileCollection
    }

    @get:Classpath
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asPath

        if (input.name.startsWith("org")) { // FIXME
            // Plugin dependency
            input.forEachDirectoryEntry {
                it.resolve("lib").listDirectoryEntries("*.jar").forEach(outputs::file)
            }
        } else {
            // IntelliJ Platform SDK dependency
            collectIntelliJPlatformDependencyJars(input).forEach {
                outputs.file(it)
            }
        }

        parameters.sourcesClasspath.forEach {
            it.copyTo(outputs.file(it.name))
        }
    }
}

internal fun DependencyHandler.applyIntellijPlatformCollectorTransformer(
    compileClasspathConfiguration: Configuration,
    testCompileClasspathConfiguration: Configuration,
    intellijPlatformSources: Configuration,
) {
    artifactTypes.maybeCreate(ZIP_TYPE)
        .attributes.attribute(Attributes.collected, false)

    compileClasspathConfiguration
        .attributes.attribute(Attributes.collected, true)

    testCompileClasspathConfiguration
        .attributes.attribute(Attributes.collected, true)

    registerTransform(IntelliJPlatformCollectorTransformer::class) {
        from
            .attribute(Attributes.extracted, true)
            .attribute(Attributes.collected, false)
        to
            .attribute(Attributes.extracted, true)
            .attribute(Attributes.collected, true)

        parameters {
            sourcesClasspath.from(intellijPlatformSources)
        }
    }
}
