// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.artifacts.transform

import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ZIP_TYPE
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.plugins.JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations
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

internal fun Project.applyIntellijPlatformCollectorTransformer() {
    project.dependencies {
        attributesSchema {
            attribute(Configurations.Attributes.collected)
        }

        artifactTypes.maybeCreate(ZIP_TYPE)
            .attributes
            .attribute(Configurations.Attributes.collected, false)

        listOf(
            configurations.getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME),
            configurations.getByName(TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME),
        ).forEach {
            it.attributes {
                attributes.attribute(Configurations.Attributes.collected, true)
            }
        }

        registerTransform(IntelliJPlatformCollectorTransformer::class) {
            from
                .attribute(Configurations.Attributes.extracted, true)
                .attribute(Configurations.Attributes.collected, false)
            to
                .attribute(Configurations.Attributes.extracted, true)
                .attribute(Configurations.Attributes.collected, true)

            parameters {
                sourcesClasspath.from(configurations.getByName(Configurations.INTELLIJ_PLATFORM_SOURCES))
            }
        }
    }
}
