// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("unused")

package org.jetbrains.intellij.platform.gradleplugin.dependencies

import org.gradle.api.GradleException
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradleplugin.BuildException
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.MINIMAL_SUPPORTED_INTELLIJ_PLATFORM_VERSION
import org.jetbrains.intellij.platform.gradleplugin.Version
import org.jetbrains.intellij.platform.gradleplugin.collectIntelliJPlatformDependencyJars
import org.jetbrains.intellij.platform.gradleplugin.model.*
import org.jetbrains.intellij.platform.gradleplugin.productInfo
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.*

fun DependencyHandler.intellijPlatformLocal(localPath: String): Dependency? {

    val ideaDir = Path.of(localPath).let {
        it.takeUnless { OperatingSystem.current().isMacOsX && it.extension == "app" } ?: it.resolve("Contents")
    }

    if (!ideaDir.exists() || !ideaDir.isDirectory()) {
        throw BuildException("Specified localPath '$localPath' doesn't exist or is not a directory")
    }

    val productInfo = ideaDir.productInfo()
    if (Version.parse(productInfo.buildNumber) < Version.parse(MINIMAL_SUPPORTED_INTELLIJ_PLATFORM_VERSION)) {
        throw GradleException("The minimal supported IDE version is $MINIMAL_SUPPORTED_INTELLIJ_PLATFORM_VERSION+, the provided version is too low: ${productInfo.version} (${productInfo.buildNumber})")
    }

    val ivyFileName = "${productInfo.productCode}-${productInfo.version}.xml"

    val dependency = create(productInfo) {
        val targetFile = Path.of(it.ivyDirectory.get()).resolve(ivyFileName)

        if (targetFile.notExists()) {
            targetFile.parent.createDirectories()
            targetFile.createFile()

            val extractor = XmlExtractor<IvyModule>()
            val ivyModule = IvyModule(
                info = IvyModuleInfo(
                    organisation = group,
                    module = name,
                    revision = version,
                    publication = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
                ),
                configurations = mutableListOf(
                    IvyModuleConfiguration(
                        name = "default",
                        visibility = "public",
                    ),
                    IvyModuleConfiguration(
                        name = "compile",
                        visibility = "public",
                    ),
                    IvyModuleConfiguration(
                        name = "sources",
                        visibility = "public",
                    ),
                    IvyModuleConfiguration(
                        name = Configurations.INTELLIJ_PLATFORM_LOCAL,
                        visibility = "public",
                    ),
                ),
                publications = collectIntelliJPlatformDependencyJars(ideaDir).map { file ->
                    IvyModulePublication(
                        name = file.relativeTo(ideaDir).pathString.removeSuffix(".${file.extension}"),
                        type = "jar",
                        ext = "jar",
//                        conf = Configurations.INTELLIJ_PLATFORM_LOCAL,
                        conf = "default",
                    )
                },
            )

            println("XXX")

            extractor.marshal(ivyModule, targetFile)

        }



        println("ideaDir = ${ideaDir}")
        println("targetFile = ${targetFile}")
    }

    return add(Configurations.INTELLIJ_PLATFORM_LOCAL, dependency)
}
