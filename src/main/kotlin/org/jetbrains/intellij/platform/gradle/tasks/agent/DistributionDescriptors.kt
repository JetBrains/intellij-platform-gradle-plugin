// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlDelegatingReader
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.newGenericReader
import nl.adaptivity.xmlutil.xmlStreaming
import org.jetbrains.intellij.platform.gradle.models.PluginDescriptor
import org.jetbrains.intellij.platform.gradle.models.xml
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

internal const val DISTRIBUTION_PLUGIN_XML = "META-INF/plugin.xml"
internal const val DISTRIBUTION_XML_LIMIT = 1024 * 1024
internal const val DISTRIBUTION_DESCRIPTOR_LIMIT = 512

internal fun readDistributionBytes(input: InputStream): ByteArray = input.readNBytes(DISTRIBUTION_XML_LIMIT + 1).also {
    require(it.size <= DISTRIBUTION_XML_LIMIT) { "Plugin descriptor exceeds the inspection limit." }
}

internal fun parseDistributionDescriptor(bytes: ByteArray, path: String, kind: String, provenance: String): DistributionDescriptor {
    val reader = DistributionXmlReader(xmlStreaming.newGenericReader(bytes.inputStream()))
    val descriptor = try {
        while (reader.hasNext()) {
            if (reader.next() == EventType.START_ELEMENT) break
        }
        require(reader.eventType == EventType.START_ELEMENT && (reader.localName == "idea-plugin" || kind == "include")) {
            "Expected an idea-plugin descriptor."
        }
        xml.decodeFromReader(PluginDescriptor.serializer(), reader, QName(reader.namespaceURI, reader.localName, reader.prefix)).also {
            while (reader.hasNext()) reader.next()
        }
    } finally {
        reader.close()
    }
    return DistributionDescriptor(
        path = path,
        kind = kind,
        provenance = provenance,
        state = "present",
        metadata = sortedMapOf(
            "id" to descriptor.id?.trim(),
            "name" to descriptor.name?.trim(),
            "version" to descriptor.version?.trim(),
            "description" to descriptor.description?.value?.trim(),
            "changeNotes" to descriptor.changeNotes?.value?.trim(),
            "sinceBuild" to descriptor.ideaVersion?.sinceBuild,
            "untilBuild" to descriptor.ideaVersion?.untilBuild,
            "vendor" to descriptor.vendor?.value?.trim(),
            "vendorEmail" to descriptor.vendor?.email,
            "vendorUrl" to descriptor.vendor?.url,
            "package" to descriptor.packageName,
            "productCode" to descriptor.productDescriptor?.code,
            "releaseDate" to descriptor.productDescriptor?.releaseDate,
            "releaseVersion" to descriptor.productDescriptor?.releaseVersion,
            "productOptional" to descriptor.productDescriptor?.optional,
            "productEap" to descriptor.productDescriptor?.eap,
        ),
        dependencies = descriptorDependencies(descriptor.depends, descriptor.dependencies),
        modules = buildList {
            descriptor.content?.modules?.forEach {
                add(DistributionModule(
                    it.name,
                    it.loading,
                    if (it.inline) null else it.name?.let { name -> "META-INF/$name.xml" },
                    it.inline,
                    descriptorDependencies(it.depends, it.dependencies),
                ))
            }
            descriptor.modules.forEach { add(DistributionModule(it.value, null, null, false)) }
        }.sortedBy { it.name },
        includes = reader.includes.sorted(),
    )
}

private fun descriptorDependencies(
    legacy: List<PluginDescriptor.LegacyDependency>,
    dependencies: PluginDescriptor.Dependencies?,
): List<DistributionDescriptorDependency> = buildList {
    legacy.forEach {
        add(DistributionDescriptorDependency("legacy", it.id.trim(), it.optional?.toBooleanStrictOrNull(), it.configFile))
    }
    listOf("module" to dependencies?.modules.orEmpty(), "plugin" to dependencies?.plugins.orEmpty()).forEach { (kind, entries) ->
        entries.forEach {
            add(DistributionDescriptorDependency(kind, it.id ?: it.name, it.optional?.toBooleanStrictOrNull(), it.configFile))
        }
    }
}.sortedWith(compareBy({ it.kind }, { it.id }, { it.configFile }))

private class DistributionXmlReader(reader: XmlReader) : XmlDelegatingReader(reader) {
    val includes = mutableListOf<String>()

    override fun next(): EventType = delegate.next().also {
        require(it != EventType.DOCDECL) { "DTDs are not allowed in plugin descriptors." }
        if (it == EventType.START_ELEMENT && localName == "include" && namespaceURI == "http://www.w3.org/2001/XInclude") {
            getAttributeValue(null, "href")?.let(includes::add)
        }
    }
}

internal fun distributionResourcePath(value: String): String {
    val path = Path.of(value.replace('\\', '/').removePrefix("/"))
    require(!path.isAbsolute && path.none { it.toString() == ".." } && ':' !in value && '#' !in value && path.normalize().toString().isNotBlank()) {
        "Descriptor references must be local resource paths."
    }
    return path.normalize().toString().replace(File.separatorChar, '/')
}

internal fun DistributionDescriptor.references(): List<Pair<String, String>> = buildList {
    dependencies.mapNotNull { it.configFile }.forEach { add(it to "optional") }
    modules.flatMap { it.dependencies }.mapNotNull { it.configFile }.forEach { add(it to "optional") }
    modules.mapNotNull { it.descriptor }.forEach { add(it to "module") }
    includes.forEach { add(it to "include") }
}.distinct().sortedBy { it.first }

internal fun readDistributionSources(snapshot: DistributionSnapshot): List<DistributionDescriptor> {
    val roots = snapshot.resourceDirectories.map(::File)
    val pending = ArrayDeque<Pair<File, String>>()
    (listOfNotNull(snapshot.inputDescriptor?.let(::File)) + roots.map { it.resolve(DISTRIBUTION_PLUGIN_XML) })
        .distinct().forEach { pending.add(it to "main") }
    val seen = mutableSetOf<String>()
    val result = mutableListOf<DistributionDescriptor>()
    if (snapshot.module) {
        roots.map { it.resolve("META-INF").toPath() }.filter { Files.isDirectory(it) }.forEach { root ->
            Files.walk(root).use { paths ->
                val xml = paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".xml") }
                    .limit(DISTRIBUTION_DESCRIPTOR_LIMIT.toLong() + 1).toList()
                require(xml.size <= DISTRIBUTION_DESCRIPTOR_LIMIT) { "Too many module descriptors." }
                xml.sorted().forEach { path ->
                    if (readDistributionDescriptorFile(path.toFile(), "module", "declared").state == "present") {
                        pending.add(path.toFile() to "module")
                    }
                }
            }
        }
    }
    while (pending.isNotEmpty()) {
        val (file, kind) = pending.removeFirst()
        val path = file.distributionPath()
        if (path in seen) continue
        require(seen.size < DISTRIBUTION_DESCRIPTOR_LIMIT) { "Too many referenced plugin descriptors." }
        seen += path
        val descriptor = readDistributionDescriptorFile(file, kind, "declared")
        result += descriptor
        descriptor.references().forEach { (reference, referenceKind) ->
            val local = runCatching { distributionResourcePath(reference) }.getOrNull()
            if (local == null) {
                result += DistributionDescriptor(reference, referenceKind, "declared", "unsupportedReference")
            } else {
                val candidates = if (local.startsWith("META-INF/")) {
                    roots.map { it.resolve(local) } + listOf(file.parentFile.resolve(local.removePrefix("META-INF/")))
                } else {
                    listOf(file.parentFile.resolve(local)) + roots.map { it.resolve("META-INF/$local") }
                }
                val existing = candidates.filter { it.isFile }.distinct()
                (existing.ifEmpty { candidates.take(1) }).forEach { pending.add(it to referenceKind) }
            }
        }
    }
    return result.distinctBy { it.path }.sortedBy { it.path }
}

internal fun readDistributionDescriptorFile(file: File, kind: String, provenance: String): DistributionDescriptor = when {
    !file.isFile -> DistributionDescriptor(file.distributionPath(), kind, provenance, "missing")
    else -> runCatching {
        file.inputStream().use { parseDistributionDescriptor(readDistributionBytes(it), file.distributionPath(), kind, provenance) }
    }.getOrElse { DistributionDescriptor(file.distributionPath(), kind, provenance, "invalid") }
}