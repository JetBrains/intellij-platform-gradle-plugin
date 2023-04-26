// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.IvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity
import org.gradle.internal.xml.SimpleXmlWriter
import org.gradle.internal.xml.XmlTransformer
import org.jetbrains.intellij.dependency.IdePluginSourceZipFilesProvider
import org.jetbrains.intellij.dependency.IdeaDependency
import org.jetbrains.intellij.dependency.IntellijIvyArtifact
import org.jetbrains.intellij.dependency.PluginDependency
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.io.Writer
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*

class IntelliJIvyDescriptorFileGenerator(private val projectIdentity: IvyPublicationIdentity) {

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

    fun addSourceArtifacts(ideaDependency: IdeaDependency?, plugin: PluginDependency, baseDir: Path, groupId: String): IntelliJIvyDescriptorFileGenerator {
        val sourcesConfiguration = DefaultIvyConfiguration("sources")
        addConfiguration(sourcesConfiguration)
        if (plugin.sourceJarFiles.isNotEmpty()) {
            plugin.sourceJarFiles.forEach {
                addArtifact(IntellijIvyArtifact.createJarDependency(it.toPath(), sourcesConfiguration.name, baseDir, groupId))
            }
        } else {
            ideaDependency
                ?.sourceZipFiles
                ?.map { it.toPath() }
                ?.let { IdePluginSourceZipFilesProvider.getSourceZips(it, plugin.platformPluginId) }
                ?.let { IntellijIvyArtifact.createZipDependency(it, sourcesConfiguration.name, ideaDependency.classes.toPath()) }
                ?.let(::addArtifact)
        }
        // see: https://github.com/JetBrains/gradle-intellij-plugin/issues/153
        ideaDependency
            ?.sources
            ?.takeIf { plugin.builtin }
            ?.let {
                val name = if (isDependencyOnPyCharm(ideaDependency)) "pycharmPC" else "ideaIC"
                val artifact = IntellijIvyArtifact(it.toPath(), name, "jar", "sources", "sources")
                artifact.conf = sourcesConfiguration.name
                addArtifact(artifact)
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
                .attribute("organisation", projectIdentity.organisation)
                .attribute("module", projectIdentity.module)
                .attribute("revision", projectIdentity.revision)
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
}
