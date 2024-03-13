// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.Constants.JETBRAINS_MARKETPLACE_MAVEN_GROUP
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.artifacts.transform.ExtractorTransformer
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.io.File.separator
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * Resolves the target directory name for the [ExtractorTransformer] dependency transformer.
 * Based on the [artifactPath], it decides about the type of the handled dependency and produces the output directory name.
 * For unknown dependencies, throws an exception.
 *
 * @param artifactPath The path of the artifact to handle.
 */
class ExtractorTransformerTargetNameResolver(
    private val artifactPath: Path,
) : Resolver<String> {

    override val subject = "Extractor Transformer Target Name"
    override val log = Logger(javaClass)

    private val coordinates by lazy {
        runCatching {
            val (groupId, artifactId, version) = artifactPath.absolutePathString().split(separator).dropLast(2).takeLast(3)
            Triple(groupId, artifactId, version)
        }.onFailure {
            throw GradleException("Unknown structure of the artifact path: $artifactPath", it)
        }.getOrNull()
    }

    override fun resolve() = sequenceOf(
        "$subject for IntelliJ Platform dependency" to {
            val (groupId, artifactId, version) = coordinates ?: return@to null
            val coordinates = Coordinates(groupId, artifactId)

            IntelliJPlatformType.values()
                .firstOrNull { it.dependency == coordinates }
                ?.let { "$it-$version" }
        },
        "$subject for JetBrains Runtime dependency" to {
            val (groupId, artifactId, version) = coordinates ?: return@to null

            version
                .takeIf { groupId == "com.jetbrains" && artifactId == "jbr" }
        },
        "$subject for JetBrains Marketplace plugin dependency" to {
            val (groupId, artifactId, version) = coordinates ?: return@to null

            val channel = when {
                groupId == JETBRAINS_MARKETPLACE_MAVEN_GROUP -> ""
                groupId.endsWith(".$JETBRAINS_MARKETPLACE_MAVEN_GROUP") -> groupId.dropLast(JETBRAINS_MARKETPLACE_MAVEN_GROUP.length + 1)
                else -> null
            } ?: return@to null

            "$groupId-$artifactId-$version" + "@$channel".takeUnless { channel.isEmpty() }.orEmpty()
        }
    ).resolve()
}
