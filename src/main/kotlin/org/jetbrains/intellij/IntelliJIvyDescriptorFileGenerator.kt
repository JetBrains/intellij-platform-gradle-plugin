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
