// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

private const val DISTRIBUTION_XML_TOTAL_LIMIT = 16 * 1024 * 1024

internal fun DistributionPlan.observeOverlays(): DistributionPlan = copy(nativeVariants = nativeVariants.map { variant ->
    variant.copy(overlays = variant.overlayRoots.flatMap { path ->
        val root = File(path).toPath()
        runCatching {
            when {
                Files.isRegularFile(root) && !Files.isSymbolicLink(root) -> listOf(DistributionArtifact("nativeOverlay", path, rootDirectory?.let { "$it/${root.fileName}" }))
                Files.isDirectory(root) && !Files.isSymbolicLink(root) -> Files.walk(root).use { paths ->
                    paths.filter { Files.isRegularFile(it) && !Files.isSymbolicLink(it) }.limit(10_001).map {
                        DistributionArtifact("nativeOverlay", it.toFile().distributionPath(), rootDirectory?.let { directory ->
                            "$directory/${root.relativize(it).toString().replace(File.separatorChar, '/')}"
                        })
                    }.toList().also { require(it.size <= 10_000) { "Too many overlay files." } }
                }
                else -> listOf(DistributionArtifact("unavailableNativeOverlay", path, null))
            }
        }.getOrElse { listOf(DistributionArtifact("unavailableNativeOverlay", path, null)) }
    }.sortedWith(compareBy({ it.destination }, { it.origin })))
})

internal fun inspectDistributionArchive(file: File, plan: DistributionPlan): DistributionArchive {
    require(file.isFile) { "The requested distribution archive does not exist." }
    val entries = mutableListOf<DistributionEntry>()
    val descriptors = mutableListOf<DistributionDescriptor>()
    ZipFile(file).use { zip ->
        val contents = zip.entries().asSequence().take(100_001).toList()
        require(contents.size <= 100_000) { "Too many distribution archive entries." }
        require(contents.map { it.name }.distinct().size == contents.size) { "Duplicate distribution archive paths." }
        val xml = sortedMapOf<String, ByteArray>()
        var xmlBytes = 0
        contents.sortedBy { it.name }.forEach { entry ->
            validateDistributionEntry(entry.name)
            if (!entry.isDirectory) {
                require(entries.size < 100_000) { "Too many observed archive entries." }
                entries += DistributionEntry(entry.name, entry.size.takeIf { it >= 0 })
                when {
                    entry.name.endsWith(".jar", ignoreCase = true) -> zip.getInputStream(entry).use { input ->
                        inspectDistributionJar(input, entry.name, entries, descriptors)
                    }
                    entry.name.endsWith(".xml") -> zip.getInputStream(entry).use { input ->
                        require(xml.size < DISTRIBUTION_DESCRIPTOR_LIMIT) { "Too many XML resources." }
                        val bytes = readDistributionBytes(input)
                        xmlBytes += bytes.size
                        require(xmlBytes <= DISTRIBUTION_XML_TOTAL_LIMIT) { "XML resources exceed the inspection limit." }
                        xml[entry.name] = bytes
                    }
                }
            }
        }
        // Also support descriptors stored directly in an exploded distribution ZIP.
        xml.keys.filter { it.endsWith("/$DISTRIBUTION_PLUGIN_XML") || it == DISTRIBUTION_PLUGIN_XML }.forEach { main ->
            val prefix = main.removeSuffix(DISTRIBUTION_PLUGIN_XML)
            descriptors += distributionArchiveDescriptors(xml.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }, prefix)
        }
    }
    val actual = entries.filterNot { '!' in it.path }.map { it.path }.toSet()
    val variant = plan.nativeVariants.takeIf { plan.nativeVariantsEnabled }?.firstOrNull { it.archivePath == file.distributionPath() }
    val expected = buildList {
        plan.artifacts.filter { it.kind == "composedJar" }.mapNotNullTo(this) { it.destination }
        plan.dependencies.filter { it.category in listOf("runtimeCandidate", "pluginModule") }.forEach { dependency ->
            dependency.destinationDirectory?.let { directory ->
                dependency.files.filterNot { File(it).isDirectory }.mapTo(this) { "$directory/${File(it).name}" }
            }
        }
        variant?.pluginJarDestination?.let { add(it) }
        variant?.overlays?.mapNotNullTo(this) { it.destination }
    }.distinct().sorted()
    return DistributionArchive(
        path = file.distributionPath(),
        entries = entries.sortedBy { it.path },
        descriptors = descriptors.distinctBy { it.path }.sortedBy { it.path },
        comparison = DistributionComparison(
            matched = expected.filter { it in actual },
            missing = expected.filterNot { it in actual },
            unplanned = (actual - expected.toSet()).sorted(),
        ),
    )
}

private fun inspectDistributionJar(input: InputStream, path: String, entries: MutableList<DistributionEntry>, descriptors: MutableList<DistributionDescriptor>) {
    val signature = input.readNBytes(4)
    require(signature.contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04)) || signature.contentEquals(byteArrayOf(0x50, 0x4b, 0x05, 0x06))) {
        "Invalid JAR in distribution archive."
    }
    ZipInputStream(java.io.SequenceInputStream(signature.inputStream(), input)).use { zip ->
        val bounded = DistributionBudgetInputStream(zip)
        val xml = sortedMapOf<String, ByteArray>()
        var xmlBytes = 0
        val names = mutableSetOf<String>()
        val buffer = ByteArray(8192)
        while (true) {
            val entry = zip.nextEntry ?: break
            require(names.size < 100_000 && names.add(entry.name)) { "Too many or duplicate JAR entries." }
            validateDistributionEntry(entry.name)
            if (!entry.isDirectory) {
                if (entry.name.endsWith(".xml")) {
                    require(xml.size < DISTRIBUTION_DESCRIPTOR_LIMIT) { "Too many XML resources." }
                    val bytes = readDistributionBytes(bounded)
                    xmlBytes += bytes.size
                    require(xmlBytes <= DISTRIBUTION_XML_TOTAL_LIMIT) { "XML resources exceed the inspection limit." }
                    xml[entry.name] = bytes
                } else {
                    while (bounded.read(buffer) != -1) {}
                }
                require(entries.size < 100_000) { "Too many observed archive entries." }
                entries += DistributionEntry("$path!${entry.name}", entry.size.takeIf { it >= 0 })
            } else {
                while (bounded.read(buffer) != -1) {}
            }
        }
        require(names.isNotEmpty() || signature[2] == 0x05.toByte()) { "Invalid empty JAR." }
        descriptors += distributionArchiveDescriptors(xml, "$path!")
    }
}

private fun distributionArchiveDescriptors(resources: Map<String, ByteArray>, prefix: String): List<DistributionDescriptor> {
    val pending = ArrayDeque<Pair<String, String>>()
    if (DISTRIBUTION_PLUGIN_XML in resources) pending.add(DISTRIBUTION_PLUGIN_XML to "main")
    val result = linkedMapOf<String, DistributionDescriptor>()
    fun visit() {
        while (pending.isNotEmpty()) {
            val (path, kind) = pending.removeFirst()
            if (path in result) continue
            require(result.size < DISTRIBUTION_DESCRIPTOR_LIMIT) { "Too many referenced descriptors." }
            val descriptor = resources[path]?.let { parseDistributionDescriptor(it, "$prefix$path", kind, "verified") }
                ?: DistributionDescriptor("$prefix$path", kind, "verified", "missing")
            result[path] = descriptor
            descriptor.references().forEach { (reference, referenceKind) ->
                val local = distributionResourcePath(reference)
                val resource = when {
                    local.startsWith("META-INF/") -> local
                    else -> path.substringBeforeLast('/', "").let { if (it.isBlank()) local else "$it/$local" }
                }
                pending.add(resource to referenceKind)
            }
        }
    }
    visit()
    // Named descriptors without a referring main descriptor have no verified role.
    resources.filterKeys { it.startsWith("META-INF/") && it.endsWith(".xml") && it !in result }.forEach { (path, bytes) ->
        val descriptor = runCatching { parseDistributionDescriptor(bytes, "$prefix$path", "additional", "verified") }.getOrNull()
        if (descriptor != null) {
            pending.add(path to "additional")
            visit()
        }
    }
    return result.values.toList()
}

private fun validateDistributionEntry(path: String) {
    require(path.isNotBlank() && '\\' !in path && '!' !in path && !path.startsWith('/') && path.split('/').none { it == ".." || it == "." } && ':' !in path) {
        "Unsafe distribution archive path."
    }
}

private class DistributionBudgetInputStream(input: InputStream) : FilterInputStream(input) {
    private var remaining = 256L * 1024 * 1024

    override fun read(): Int = super.read().also { if (it >= 0) consume(1) }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int = `in`.read(bytes, offset, length).also {
        if (it > 0) consume(it)
    }

    private fun consume(size: Int) {
        remaining -= size
        require(remaining >= 0) { "Expanded JAR exceeds the inspection limit." }
    }
}