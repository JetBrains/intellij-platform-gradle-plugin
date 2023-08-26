// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.artifacts.transform

import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ZIP_TYPE
import org.gradle.api.attributes.Attribute
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
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.INTELLIJ_PLATFORM_SOURCES_CONFIGURATION_NAME
import org.jetbrains.intellij.platform.gradleplugin.asPath
import org.jetbrains.intellij.platform.gradleplugin.collectJars
import org.jetbrains.intellij.platform.gradleplugin.isKotlinRuntime
import kotlin.io.path.isDirectory
import kotlin.io.path.name

@DisableCachingByDefault(because = "Not worth caching")
abstract class IntelliJPlatformCollectorTransformer : TransformAction<IntelliJPlatformCollectorTransformer.Parameters> {

    interface Parameters : TransformParameters {

        @get:CompileClasspath
        val sourcesClasspath: ConfigurableFileCollection
    }

    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asPath
        val withKotlin = true // TODO: detect if project uses Kotlin
        val lib = input.resolve("lib")

        // TODO: cleanup
        if (lib.isDirectory()) {
            val baseFiles = (collectJars(lib) { file ->
                (withKotlin || !isKotlinRuntime(file.name.removeSuffix(".jar"))) && file.name != "junit.jar" && file.name != "annotations.jar"
            }).sorted()

            val antFiles = collectJars(lib.resolve("ant/lib")).sorted()
            (baseFiles + antFiles).forEach {
                outputs.file(it)
            }
        }

        parameters.sourcesClasspath.forEach {
            it.copyTo(outputs.file(it.name))
        }
    }
}

internal fun Project.applyIntellijPlatformCollectorTransformer() {
    val extractedAttribute = Attribute.of("intellijPlatformExtracted", Boolean::class.javaObjectType)
    val collectedAttribute = Attribute.of("intellijPlatformCollected", Boolean::class.javaObjectType)

    project.dependencies {
        attributesSchema {
            attribute(collectedAttribute)
        }

        artifactTypes.maybeCreate(ZIP_TYPE)
            .attributes
            .attribute(collectedAttribute, false)

        listOf(
            configurations.getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME),
            configurations.getByName(TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME),
        ).forEach {
            it.attributes {
                attributes.attribute(collectedAttribute, true)
            }
        }

        registerTransform(IntelliJPlatformCollectorTransformer::class) {
            from
                .attribute(extractedAttribute, true)
                .attribute(collectedAttribute, false)
            to
                .attribute(extractedAttribute, true)
                .attribute(collectedAttribute, true)
            parameters {
                sourcesClasspath.from(configurations.getByName(INTELLIJ_PLATFORM_SOURCES_CONFIGURATION_NAME))
            }
        }
    }
}
