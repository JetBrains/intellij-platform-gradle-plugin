// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import kotlinx.serialization.Serializable
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.jetbrains.intellij.platform.gradle.models.SourceCoordinate
import java.util.UUID

@Serializable
internal data class SourceArtifactOutcome(
    val coordinate: SourceCoordinate,
    val archive: String?,
    val availability: String,
    val selectedCoordinate: SourceCoordinate? = null,
    val diagnostics: List<String> = emptyList(),
)

/** Failed lenient artifact views must be recreated rather than restored as permanently empty collections. */
internal abstract class IntelliJPlatformSourcesRetryValueSource : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String = UUID.randomUUID().toString()
}
