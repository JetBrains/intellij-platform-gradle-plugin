// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/**
 * Parser for module descriptors in IntelliJ IDEA plugin JAR files.
 */
internal object ModuleDescriptorsParser {

    private val cache = ConcurrentHashMap<String, Map<String, CollectedModuleDescriptor>>()
    private val xmlInputFactory = ThreadLocal.withInitial {
        XMLInputFactory.newFactory().apply {
            runCatching { setProperty(XMLInputFactory.SUPPORT_DTD, false) }
            runCatching { setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false) }
        }
    }

    fun load(moduleDescriptorsFile: Path) =
        cache.computeIfAbsent(moduleDescriptorsFile.safePathString) {
            JarFile(moduleDescriptorsFile.toFile()).use { jarFile ->
                buildMap {
                    val entries = jarFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.name.endsWith(".xml") || entry.name.contains('/')) {
                            continue
                        }

                        jarFile.getInputStream(entry).use { inputStream ->
                            parse(inputStream)?.let { put(it.name, it) }
                        }
                    }
                }
            }
        }

    private fun parse(inputStream: InputStream): CollectedModuleDescriptor? {
        val reader = xmlInputFactory.get().createXMLStreamReader(inputStream)
        var name: String? = null
        var path: String? = null
        var inDependencies = false
        val dependencies = mutableListOf<CollectedModuleDescriptor.Dependency>()

        return try {
            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                        "module" -> {
                            val moduleName = reader.getAttributeValue(null, "name") ?: continue
                            when {
                                inDependencies -> dependencies += CollectedModuleDescriptor.Dependency(moduleName)
                                name == null -> name = moduleName
                            }
                        }

                        "dependencies" -> inDependencies = true
                        "resource-root" -> path = reader.getAttributeValue(null, "path")?.removePrefix("../")
                    }

                    XMLStreamConstants.END_ELEMENT -> {
                        if (reader.localName == "dependencies") {
                            inDependencies = false
                        }
                    }
                }
            }

            name?.let { CollectedModuleDescriptor(it, dependencies, path) }
        } finally {
            reader.close()
        }
    }
}

internal data class CollectedModuleDescriptor(
    val name: String,
    val dependencies: List<Dependency>,
    val path: String?,
) {
    data class Dependency(
        val name: String,
    )
}
