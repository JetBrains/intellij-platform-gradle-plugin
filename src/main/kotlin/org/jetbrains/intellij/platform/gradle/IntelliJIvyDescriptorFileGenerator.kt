// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.IvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.internal.xml.SimpleXmlWriter
import org.gradle.internal.xml.XmlTransformer
import org.jetbrains.intellij.platform.gradle.dependency.IntellijIvyArtifact
import org.jetbrains.intellij.platform.gradle.dependency.PluginDependency
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.io.Writer
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*

class IntelliJIvyDescriptorFileGenerator(private val coordinates: IvyCoordinates) {

    private val ivyFileEncoding = "UTF-8"
    private val ivyDatePattern = "yyyyMMddHHmmss"
    private val ivyDateFormat = SimpleDateFormat(ivyDatePattern)
    private val xmlTransformer = XmlTransformer()
    private val configurations = mutableListOf<IvyConfiguration>()
    private val artifacts = mutableListOf<IvyArtifact>()

    fun addConfiguration(ivyConfiguration: IvyConfiguration): IntelliJIvyDescriptorFileGenerator {
        configurations.add(ivyConfiguration)
        return this
    }

    fun addArtifact(ivyArtifact: IvyArtifact): IntelliJIvyDescriptorFileGenerator {
        artifacts.add(ivyArtifact)
        return this
    }

    fun addCompileArtifacts(plugin: PluginDependency, baseDir: Path, groupId: String): IntelliJIvyDescriptorFileGenerator {
        val compileConfiguration = DefaultIvyConfiguration("compile")

        addConfiguration(compileConfiguration)

        plugin.jarFiles.forEach {
            addArtifact(IntellijIvyArtifact.createJarDependency(it.toPath(), compileConfiguration.name, baseDir, groupId))
        }
        plugin.classesDirectory?.let {
            addArtifact(IntellijIvyArtifact.createDirectoryDependency(it.toPath(), compileConfiguration.name, baseDir, groupId))
        }
        plugin.metaInfDirectory?.let {
            addArtifact(IntellijIvyArtifact.createDirectoryDependency(it.toPath(), compileConfiguration.name, baseDir, groupId))
        }

        return this
    }

    @Deprecated("Use writeTo(path: Path) instead")
    fun writeTo(file: File) {
        xmlTransformer.transform(file, ivyFileEncoding) {
            try {
                writeDescriptor(this)
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
        }
    }

    fun writeTo(path: Path) = xmlTransformer.transform(path.toFile(), ivyFileEncoding) {
        try {
            writeDescriptor(this)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    @Throws(IOException::class)
    private fun writeDescriptor(writer: Writer) {
        OptionalAttributeXmlWriter(writer, "  ", ivyFileEncoding).run {
            startElement("ivy-module").attribute("version", "2.0")
            if (usesClassifier()) {
                attribute("xmlns:m", "https://ant.apache.org/ivy/maven")
            }
            startElement("info")
                .attribute("organisation", coordinates.organisation)
                .attribute("module", coordinates.module)
                .attribute("revision", coordinates.revision)
                .attribute("publication", ivyDateFormat.format(Date()))
            endElement()

            writeConfigurations()
            writePublications()
            endElement()
        }
    }

    private fun usesClassifier() = artifacts.any { it.classifier != null }

    @Throws(IOException::class)
    private fun OptionalAttributeXmlWriter.writeConfigurations() {
        startElement("configurations")
        configurations.forEach {
            startElement("conf")
                .attribute("name", it.name)
                .attribute("visibility", "public")
            if (it.extends.size > 0) {
                attribute("extends", it.extends.joinToString(","))
            }
            endElement()
        }
        endElement()
    }

    @Throws(IOException::class)
    private fun OptionalAttributeXmlWriter.writePublications() {
        startElement("publications")
        artifacts.forEach {
            startElement("artifact")
                .attribute("name", it.name)
                .attribute("type", it.type)
                .attribute("ext", it.extension)
                .attribute("conf", it.conf)
                .attribute("m:classifier", it.classifier)
                .endElement()
        }
        endElement()
    }

    class OptionalAttributeXmlWriter(writer: Writer, indent: String, encoding: String) : SimpleXmlWriter(writer, indent, encoding) {

        override fun startElement(name: String?): OptionalAttributeXmlWriter {
            super.startElement(name)
            return this
        }

        override fun attribute(name: String?, value: String?): OptionalAttributeXmlWriter {
            if (value != null) {
                super.attribute(name, value)
            }
            return this
        }
    }

    data class IvyCoordinates(
        val organisation: String,
        val module: String,
        val revision: String,
    )
}
