// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.Serializable

@Serializable
internal data class SourceOwner(
    val ref: String,
    val id: String,
    val name: String?,
    val kind: String,
    val aliases: List<String>,
    val dependencies: List<String>,
    val archives: List<String>,
)

@Serializable
internal data class SourceDiscoveryInput(
    val root: String,
    val productCode: String,
    val version: String,
    val buildNumber: String,
    val binaryPaths: List<String>,
    val owners: List<SourceOwner>,
    val owner: String? = null,
)

@Serializable
internal data class SourceCoordinate(val group: String, val artifact: String, val version: String, val classifier: String = "sources") {
    val notation get() = "$group:$artifact:$version:$classifier"
}

@Serializable
internal data class SourceBinary(
    val id: String,
    val path: String,
    val owners: List<String>,
    val exists: Boolean,
    val fingerprint: String?,
    val inspection: String,
    val diagnostics: List<String>,
    val metadataEntries: List<String> = emptyList(),
)

@Serializable
internal data class SourceRecord(
    val id: String,
    val binaries: List<String>,
    val owners: List<String>,
    val origin: String,
    val candidates: List<SourceCoordinate> = emptyList(),
    val selectedCoordinate: SourceCoordinate? = null,
    val provenance: String = "unverified",
    val evidence: List<String> = emptyList(),
    val availability: String = "notRequested",
    val archive: String? = null,
    val extractedRoot: String? = null,
    val extractedRoots: List<String> = emptyList(),
    val extraction: String = "notRequested",
    val diagnostics: List<String> = emptyList(),
    val candidateProvenance: List<String> = emptyList(),
)

@Serializable
internal data class SourcePlan(
    val input: SourceDiscoveryInput,
    val inventoryFingerprint: String,
    val binaries: List<SourceBinary>,
    val sources: List<SourceRecord>,
    val diagnostics: List<String>,
    val ruleSchema: String,
)
