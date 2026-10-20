// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import org.w3c.dom.Element
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal const val DISTRIBUTION_PLUGIN_XML = "META-INF/plugin.xml"
internal const val DISTRIBUTION_XML_LIMIT = 1024 * 1024
internal const val DISTRIBUTION_DESCRIPTOR_LIMIT = 512

internal fun readDistributionBytes(input: InputStream): ByteArray = input.readNBytes(DISTRIBUTION_XML_LIMIT + 1).also {
    require(it.size <= DISTRIBUTION_XML_LIMIT) { "Plugin descriptor exceeds the inspection limit." }
}

internal fun parseDistributionDescriptor(bytes: ByteArray, path: String, kind: String, provenance: String): DistributionDescriptor {
    val factory = DocumentBuilderFactory.newDefaultInstance().apply {
        isNamespaceAware = true
        isXIncludeAware = false
        isExpandEntityReferences = false
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    }
    val builder = factory.newDocumentBuilder().apply {
        setErrorHandler(object : ErrorHandler {
            override fun warning(exception: SAXParseException) = throw exception
            override fun error(exception: SAXParseException) = throw exception
            override fun fatalError(exception: SAXParseException) = throw exception
        })
    }
    val root = builder.parse(bytes.inputStream()).documentElement
    require(root.localName == "idea-plugin" || kind == "include") { "Expected an idea-plugin descriptor." }
    fun child(name: String) = root.elements().firstOrNull { it.localName == name }
    fun text(name: String) = child(name)?.textContent?.trim()
    return DistributionDescriptor(
        path = path,
        kind = kind,
        provenance = provenance,
        state = "present",
        metadata = sortedMapOf(
            "id" to text("id"),
            "name" to text("name"),
            "version" to text("version"),
            "description" to text("description"),
            "changeNotes" to text("change-notes"),
            "sinceBuild" to child("idea-version")?.attribute("since-build"),
            "untilBuild" to child("idea-version")?.attribute("until-build"),
            "vendor" to text("vendor"),
            "vendorEmail" to child("vendor")?.attribute("email"),
            "vendorUrl" to child("vendor")?.attribute("url"),
            "package" to root.attribute("package"),
            "productCode" to child("product-descriptor")?.attribute("code"),
            "releaseDate" to child("product-descriptor")?.attribute("release-date"),
            "releaseVersion" to child("product-descriptor")?.attribute("release-version"),
            "productOptional" to child("product-descriptor")?.attribute("optional"),
            "productEap" to child("product-descriptor")?.attribute("eap"),
        ),
        dependencies = root.descriptorDependencies(),
        modules = buildList {
            child("content")?.elements()?.filter { it.localName == "module" }?.forEach {
                val inline = it.elements().isNotEmpty()
                add(DistributionModule(it.attribute("name"), it.attribute("loading"), if (inline) null else it.attribute("name")?.let { name -> "META-INF/$name.xml" }, inline, it.descriptorDependencies()))
            }
            root.elements().filter { it.localName == "module" }.forEach {
                add(DistributionModule(it.attribute("value"), null, null, false))
            }
        }.sortedBy { it.name },
        includes = buildList {
            fun includes(element: Element) {
                if (element.localName == "include" && element.namespaceURI == "http://www.w3.org/2001/XInclude") {
                    element.attribute("href")?.let { add(it) }
                }
                element.elements().forEach(::includes)
            }
            includes(root)
        }.sorted(),
    )
}

private fun Element.elements() = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
private fun Element.attribute(name: String): String? = if (hasAttribute(name)) getAttribute(name) else null

private fun Element.descriptorDependencies(): List<DistributionDescriptorDependency> = buildList {
    elements().filter { it.localName == "depends" }.forEach {
        add(DistributionDescriptorDependency("legacy", it.textContent.trim(), it.attribute("optional")?.toBooleanStrictOrNull(), it.attribute("config-file")))
    }
    elements().firstOrNull { it.localName == "dependencies" }?.elements()?.forEach {
        if (it.localName in listOf("plugin", "module")) {
            add(DistributionDescriptorDependency(it.localName, it.attribute("id") ?: it.attribute("name"), it.attribute("optional")?.toBooleanStrictOrNull(), it.attribute("config-file")))
        }
    }
}.sortedWith(compareBy({ it.kind }, { it.id }, { it.configFile }))

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