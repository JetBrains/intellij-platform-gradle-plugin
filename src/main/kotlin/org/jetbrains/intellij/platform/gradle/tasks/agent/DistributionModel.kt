// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.Serializable

@Serializable
internal data class DistributionSnapshot(
    val projectDirectory: String,
    val module: Boolean,
    val configuration: Map<String, String?>,
    val settings: Map<String, Boolean?>,
    val resourceDirectories: List<String>,
    val inputDescriptor: String?,
    val patchedDescriptor: String?,
    val plan: DistributionPlan,
)

@Serializable
internal data class DistributionData(
    val module: Boolean,
    val configuration: Map<String, String?>,
    val configurationProvenance: String = "declared",
    val settings: Map<String, Boolean?>,
    val resourceDirectories: List<String>,
    val descriptorPaths: Map<String, String?>,
    val declaredDescriptors: List<DistributionDescriptor>,
    val effectiveDescriptor: DistributionDescriptor?,
    val plan: DistributionPlan,
    val archive: DistributionArchive? = null,
)

@Serializable
internal data class DistributionPlan(
    val archivePath: String?,
    val rootDirectory: String?,
    val artifacts: List<DistributionArtifact>,
    val dependencies: List<DistributionDependency>,
    val configurations: Map<String, List<String>>,
    val nativeVariantsEnabled: Boolean,
    val nativeVariants: List<DistributionVariant>,
    val provenance: String = "declared",
)

@Serializable
internal data class DistributionArtifact(
    val kind: String,
    val origin: String?,
    val destination: String?,
    val configuration: String? = null,
    val provenance: String = "declared",
)

@Serializable
internal data class DistributionDependency(
    val configuration: String,
    val category: String,
    val packagingRole: String,
    val group: String?,
    val name: String,
    val version: String?,
    val projectPath: String?,
    val destinationDirectory: String?,
    val files: List<String> = emptyList(),
    val filesUnavailable: Boolean = false,
    val provenance: String = "declared",
)

@Serializable
internal data class DistributionVariant(
    val operatingSystem: String,
    val architecture: String,
    val archivePath: String?,
    val pluginJar: String?,
    val pluginJarDestination: String?,
    val version: String?,
    val requiredModules: List<String>,
    val overlayRoots: List<String>,
    val overlaySourcesUnavailable: Boolean,
    val overlays: List<DistributionArtifact> = emptyList(),
    val overlayRule: String = "Variant plugin JAR and overlay files are additional packaging inputs; duplicate handling and custom copy specs are not evaluated.",
    val provenance: String = "declared",
)

@Serializable
internal data class DistributionDescriptor(
    val path: String,
    val kind: String,
    val provenance: String,
    val state: String,
    val metadata: Map<String, String?> = emptyMap(),
    val dependencies: List<DistributionDescriptorDependency> = emptyList(),
    val modules: List<DistributionModule> = emptyList(),
    val includes: List<String> = emptyList(),
)

@Serializable
internal data class DistributionDescriptorDependency(
    val kind: String,
    val id: String?,
    val optional: Boolean?,
    val configFile: String?,
)

@Serializable
internal data class DistributionModule(
    val name: String?,
    val loading: String?,
    val descriptor: String?,
    val inline: Boolean,
    val dependencies: List<DistributionDescriptorDependency> = emptyList(),
)

@Serializable
internal data class DistributionArchive(
    val path: String,
    val provenance: String = "verified",
    val entries: List<DistributionEntry>,
    val descriptors: List<DistributionDescriptor>,
    val comparison: DistributionComparison,
)

@Serializable
internal data class DistributionEntry(val path: String, val size: Long?)

@Serializable
internal data class DistributionComparison(
    val scope: String = "Unrenamed observable candidate paths only; no byte comparison, collision resolution, freshness check, or completeness assertion. Unplanned entries are not necessarily unexpected.",
    val matched: List<String>,
    val missing: List<String>,
    val unplanned: List<String>,
)