package org.jetbrains.intellij

import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.IvyConfiguration
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity
import org.gradle.internal.xml.SimpleXmlWriter
import org.gradle.internal.xml.XmlTransformer
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date

class IntelliJIvyDescriptorFileGenerator(private val projectIdentity: IvyPublicationIdentity) {

    private val ivyFileEncoding = "UTF-8"
    private val ivyDatePattern = "yyyyMMddHHmmss"
    private val ivyDateFormat = SimpleDateFormat(ivyDatePattern)
    private val xmlTransformer = XmlTransformer()
    private val configurations = mutableListOf<IvyConfiguration>()
    private val artifacts = mutableListOf<IvyArtifact>()

    fun addConfiguration(ivyConfiguration: IvyConfiguration) {
        configurations.add(ivyConfiguration)
    }

    fun addArtifact(ivyArtifact: IvyArtifact) {
        artifacts.add(ivyArtifact)
    }

    fun writeTo(file: File) {
        xmlTransformer.transform(file, ivyFileEncoding) { writer ->
            try {
                writeDescriptor(writer)
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
        }
    }

    @Throws(IOException::class)
    private fun writeDescriptor(writer: Writer) {
        val xmlWriter = OptionalAttributeXmlWriter(writer, "  ", ivyFileEncoding)
        xmlWriter.startElement("ivy-module").attribute("version", "2.0")
        if (usesClassifier()) {
            xmlWriter.attribute("xmlns:m", "https://ant.apache.org/ivy/maven")
        }
        xmlWriter.startElement("info")
            .attribute("organisation", projectIdentity.organisation)
            .attribute("module", projectIdentity.module)
            .attribute("revision", projectIdentity.revision)
            .attribute("publication", ivyDateFormat.format(Date()))
        xmlWriter.endElement()

        writeConfigurations(xmlWriter)
        writePublications(xmlWriter)
        xmlWriter.endElement()
    }

    private fun usesClassifier() = artifacts.any { it.classifier != null }

    @Throws(IOException::class)
    private fun writeConfigurations(xmlWriter: OptionalAttributeXmlWriter) {
        xmlWriter.startElement("configurations")
        configurations.forEach {
            xmlWriter.startElement("conf")
                .attribute("name", it.name)
                .attribute("visibility", "public")
            if (it.extends.size > 0) {
                xmlWriter.attribute("extends", it.extends.joinToString(","))
            }
            xmlWriter.endElement()
        }
        xmlWriter.endElement()
    }

    @Throws(IOException::class)
    private fun writePublications(xmlWriter: OptionalAttributeXmlWriter) {
        xmlWriter.startElement("publications")
        artifacts.forEach {
            xmlWriter.startElement("artifact")
                .attribute("name", it.name)
                .attribute("type", it.type)
                .attribute("ext", it.extension)
                .attribute("conf", it.conf)
                .attribute("m:classifier", it.classifier)
                .endElement()
        }
        xmlWriter.endElement()
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
