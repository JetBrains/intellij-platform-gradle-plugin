// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import org.gradle.api.Project
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.platform.gradle.tasks.PreparePluginVariantTask
import org.jetbrains.intellij.platform.gradle.utils.isModule
import org.jetbrains.intellij.platform.gradle.variants
import java.io.File
import java.nio.file.Path

internal fun Project.distributionSnapshot(): DistributionSnapshot {
    val extension = extensions.findByType<IntelliJPlatformExtension>()
    val plugin = extension?.pluginConfiguration
    val patch = tasks.findByName(Tasks.PATCH_PLUGIN_XML) as? PatchPluginXmlTask
    val buildPlugin = tasks.findByName(Tasks.BUILD_PLUGIN) as? AbstractArchiveTask
    val composedJar = tasks.findByName(Tasks.COMPOSED_JAR) as? AbstractArchiveTask
    val root = buildPlugin?.archiveBaseName?.orNull
    val lib = root?.let { "$it/lib" }
    val module = pluginManager.isModule
    val categories = linkedMapOf(
        "api" to "runtimeCandidate",
        "implementation" to "runtimeCandidate",
        "runtimeOnly" to "runtimeCandidate",
        "compileOnly" to "compileOnly",
        "compileOnlyApi" to "compileOnly",
        Configurations.INTELLIJ_PLATFORM_DEPENDENCY to "platform",
        Configurations.INTELLIJ_PLATFORM_LOCAL to "platform",
        Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY to "platformPlugin",
        Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL to "platformPlugin",
        Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS to "bundledPlugin",
        Configurations.INTELLIJ_PLATFORM_BUNDLED_MODULES to "bundledModule",
        Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE to "pluginModule",
        Configurations.INTELLIJ_PLATFORM_PLUGIN_COMPOSED_MODULE to "composedModule",
    )
    val dependencies = mutableListOf<DistributionDependency>()
    categories.forEach { (configurationName, category) ->
        // Iteration would realize deferred platform/bundled declarations. Observe only already declared dependencies.
        configurations.findByName(configurationName)?.dependencies?.configureEach {
            val sources = (this as? FileCollectionDependency)?.files as? ConfigurableFileCollection
            val paths = sources?.let { distributionLiteralPaths(it.from, projectDir) }
            dependencies += DistributionDependency(
                configuration = configurationName,
                category = category,
                packagingRole = when (category) {
                    "runtimeCandidate" -> "runtimeLibraryCandidate"
                    "pluginModule" -> "separateModuleJar"
                    "composedModule" -> "mergedIntoComposedJar"
                    else -> "notPackagedByThisDeclaration"
                },
                group = group,
                name = name,
                version = version,
                projectPath = (this as? ProjectDependency)?.path,
                destinationDirectory = when (category) {
                    "runtimeCandidate" -> lib
                    "pluginModule" -> lib?.let { "$it/modules" }
                    else -> null
                },
                files = paths?.first.orEmpty(),
                filesUnavailable = this is FileCollectionDependency && (paths == null || paths.second),
            )
        }
    }
    val configurationNames = categories.keys + listOf(
        "compileClasspath", "runtimeClasspath",
        Configurations.INTELLIJ_PLATFORM_SANDBOX_RUNTIME_CLASSPATH,
    )
    val artifacts = buildList {
        composedJar?.archiveFile?.orNull?.asFile?.let { file ->
            add(DistributionArtifact("composedJar", file.distributionPath(), lib?.let { "$it/${file.name}" }))
        }
        (tasks.findByName(Tasks.JAR_SEARCHABLE_OPTIONS) as? AbstractArchiveTask)?.archiveFile?.orNull?.asFile?.let { file ->
            add(DistributionArtifact("searchableOptionsCandidate", file.distributionPath(), lib?.let { "$it/${file.name}" }))
        }
    }
    val nativeVariants = extension?.nativeVariants
    return DistributionSnapshot(
        projectDirectory = projectDir.distributionPath(),
        module = module,
        configuration = sortedMapOf(
            "id" to plugin?.id?.orNull,
            "name" to plugin?.name?.orNull,
            "version" to plugin?.version?.orNull,
            "description" to plugin?.description?.orNull,
            "changeNotes" to plugin?.changeNotes?.orNull,
            "vendor" to plugin?.vendor?.name?.orNull,
            "vendorEmail" to plugin?.vendor?.email?.orNull,
            "vendorUrl" to plugin?.vendor?.url?.orNull,
            "productCode" to plugin?.productDescriptor?.code?.orNull,
            "releaseDate" to plugin?.productDescriptor?.releaseDate?.orNull,
            "releaseVersion" to plugin?.productDescriptor?.releaseVersion?.orNull,
            "productOptional" to plugin?.productDescriptor?.optional?.orNull?.toString(),
            "productEap" to plugin?.productDescriptor?.eap?.orNull?.toString(),
            // Even isPresent/orNull on sinceBuild can resolve the platform through its convention.
            "sinceBuild" to null,
            "untilBuild" to plugin?.ideaVersion?.untilBuild?.orNull,
            "projectName" to extension?.projectName?.orNull,
        ),
        settings = sortedMapOf(
            "instrumentCode" to extension?.instrumentCode?.orNull,
            "buildSearchableOptions" to extension?.buildSearchableOptions?.orNull,
            "splitMode" to extension?.splitMode?.orNull,
        ),
        resourceDirectories = extensions.findByType<JavaPluginExtension>()?.sourceSets
            ?.findByName(SourceSet.MAIN_SOURCE_SET_NAME)?.resources?.srcDirs.orEmpty()
            .map { it.distributionPath() }.sorted(),
        inputDescriptor = patch?.inputFile?.orNull?.asFile?.distributionPath(),
        patchedDescriptor = patch?.outputFile?.orNull?.asFile?.distributionPath(),
        plan = DistributionPlan(
            archivePath = buildPlugin?.archiveFile?.orNull?.asFile?.distributionPath(),
            rootDirectory = root,
            artifacts = artifacts.sortedBy { it.kind },
            dependencies = dependencies.distinct().sortedWith(compareBy({ it.configuration }, { it.group }, { it.name }, { it.version }, { it.projectPath })),
            configurations = configurationNames.mapNotNull { name ->
                configurations.findByName(name)?.let { name to it.extendsFrom.map { parent -> parent.name }.sorted() }
            }.toMap().toSortedMap(),
            nativeVariantsEnabled = nativeVariants?.enabled?.orNull == true,
            nativeVariants = variants.map { variant ->
                val sources = nativeVariants?.get(variant)?.let { distributionLiteralPaths(it.from, projectDir) }
                val variantTask = tasks.findByName(PreparePluginVariantTask.taskName(variant)) as? PreparePluginVariantTask
                val variantArchive = tasks.findByName("${Tasks.BUILD_PLUGIN_VARIANTS}_${variant.os}_${variant.arch}") as? AbstractArchiveTask
                val variantJarName = variantTask?.inputJar?.orNull?.asFile?.name
                DistributionVariant(
                    operatingSystem = variant.os,
                    architecture = variant.arch,
                    archivePath = variantArchive?.archiveFile?.orNull?.asFile?.distributionPath(),
                    pluginJar = variantTask?.outputDirectory?.orNull?.asFile?.let { directory ->
                        variantJarName?.let { directory.resolve(it).distributionPath() }
                    },
                    pluginJarDestination = variantArchive?.archiveBaseName?.orNull?.let { directory -> variantJarName?.let { "$directory/lib/$it" } },
                    version = variantTask?.pluginVersion?.orNull,
                    requiredModules = listOf("com.intellij.modules.os.${variant.os}", "com.intellij.modules.arch.${variant.arch}"),
                    overlayRoots = sources?.first.orEmpty(),
                    overlaySourcesUnavailable = sources?.second == true,
                )
            },
        ),
    )
}

internal fun File.distributionPath(): String = toPath().toAbsolutePath().normalize().toString().replace(File.separatorChar, '/')

internal fun distributionLiteralPaths(sources: Iterable<*>, directory: File): Pair<List<String>, Boolean> {
    val paths = mutableListOf<String>()
    var unavailable = false
    fun visit(source: Any?) {
        when (source) {
            null -> Unit
            is File -> paths += (if (source.isAbsolute) source else directory.resolve(source.path)).distributionPath()
            is Path -> visit(source.toFile())
            is String -> paths += directory.resolve(source).distributionPath()
            is FileSystemLocation -> paths += source.asFile.distributionPath()
            is ConfigurableFileCollection -> source.from.forEach(::visit)
            is FileCollection -> unavailable = true
            is Collection<*> -> source.forEach(::visit)
            is Array<*> -> source.forEach(::visit)
            // Do not call Provider.get(), FileCollection.files, Task outputs, or arbitrary closures.
            else -> unavailable = true
        }
    }
    sources.forEach(::visit)
    return paths.distinct().sorted() to unavailable
}