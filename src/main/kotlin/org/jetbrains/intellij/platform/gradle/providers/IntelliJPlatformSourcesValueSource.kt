// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import kotlinx.serialization.json.Json
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.jetbrains.intellij.platform.gradle.models.SourceDiscoveryInput
import org.jetbrains.intellij.platform.gradle.models.SourceOwner
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.resolvers.sources.IntelliJPlatformSourceResolver
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import kotlin.io.path.Path

/** Re-evaluates content identities rather than retaining path/build-only inspection snapshots. */
internal abstract class IntelliJPlatformSourcesValueSource : ValueSource<String, IntelliJPlatformSourcesValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val root: Property<String>
        val binaryPaths: ListProperty<String>
        val owner: Property<String>
        val owners: Property<String>
        val cacheDirectory: Property<String>
        val ruleSchema: Property<String>
        val scannerSchema: Property<String>
    }

    override fun obtain(): String {
        require(parameters.ruleSchema.get() == IntelliJPlatformSourceResolver.RULE_SCHEMA)
        require(parameters.scannerSchema.get() == IntelliJPlatformSourceResolver.SCANNER_SCHEMA)
        val root = Path(parameters.root.get()).toAbsolutePath().normalize()
        val product = root.productInfo()
        val owners = Json.decodeFromString<List<SourceOwner>>(parameters.owners.get())
        val input = SourceDiscoveryInput(root.safePathString, product.productCode, product.version, product.buildNumber, parameters.binaryPaths.get(), owners, parameters.owner.orNull)
        return Json.encodeToString(IntelliJPlatformSourceResolver(Path(parameters.cacheDirectory.get())).discover(input))
    }
}