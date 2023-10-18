// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("unused")

package org.jetbrains.intellij.platform.gradleplugin.dependencies

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.create
import org.jetbrains.intellij.platform.gradleplugin.BuildException
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations.INTELLIJ_PLATFORM_LOCAL_INSTANCE
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Dependencies
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.MINIMAL_SUPPORTED_INTELLIJ_PLATFORM_VERSION
import org.jetbrains.intellij.platform.gradleplugin.Version
import org.jetbrains.intellij.platform.gradleplugin.asPath
import org.jetbrains.intellij.platform.gradleplugin.model.*
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.*

val Project.intellijPlatformLocal: DependencyHandler.(String) -> Dependency?
    get() = { localPath ->
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
        val dependency = create(productInfo) { settings ->
            settings.ivyDirectory.asPath
                .resolve(ivyFileName)
                .takeIf { it.notExists() }
                ?.run {
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
                        ),
                        publications = mutableListOf(
                            IvyModulePublication(
                                name = ideaDir.pathString,
                                type = "directory",
                                ext = null,
                                conf = "default",
                            )
                        ),
                    )
                    extractor.marshal(ivyModule, createFile())
                }

            this@intellijPlatformLocal.repositories.ivy {
                val ivyDirectory = settings.ivyDirectory.asPath

                url = ivyDirectory.toUri()
                ivyPattern("$ivyDirectory/[module]-[revision].[ext]")
                artifactPattern(ideaDir.absolutePathString())
//                artifactPattern("$ivyDirectory/[artifact]")
//                artifactPattern("/Users/hsz/.gradle/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/2023.2/5bcf969924aafaa0d1b4d4602ff824251b1004f6/[artifact](-[classifier])(.[ext])")
//                artifactPattern("/[artifact]")


//                artifactPattern("/Users/hsz/Projects/JetBrains/intellij-plugin-template/.gradle/intellijPlatform/sources/ideaIC-2023.2-[classifier].jar")
//                artifactPattern(ideaDir.absolutePathString())
            }
        }

        add(INTELLIJ_PLATFORM_LOCAL_INSTANCE, dependency)
    }

internal fun DependencyHandler.create(
    productInfo: ProductInfo,
    settings: IntelliJPlatformDependencySettings = intellijPlatformDependencySettings,
    action: DependencyAction = {},
) = create(
    group = Dependencies.INTELLIJ_PLATFORM_LOCAL_GROUP,
    name = productInfo.productCode,
    version = productInfo.version,
).apply {
    action(this, settings)
}
