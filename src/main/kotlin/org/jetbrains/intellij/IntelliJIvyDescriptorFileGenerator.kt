package org.jetbrains.intellij

import org.gradle.api.Action
import org.gradle.api.UncheckedIOException
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.IvyConfiguration
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity
import org.gradle.internal.xml.SimpleXmlWriter
import org.gradle.internal.xml.XmlTransformer
import org.gradle.util.CollectionUtils

import java.text.SimpleDateFormat

class IntelliJIvyDescriptorFileGenerator {
    private static final String IVY_FILE_ENCODING = "UTF-8"
    private static final String IVY_DATE_PATTERN = "yyyyMMddHHmmss"

    private final IvyPublicationIdentity projectIdentity
    private final SimpleDateFormat ivyDateFormat = new SimpleDateFormat(IVY_DATE_PATTERN)
    private final XmlTransformer xmlTransformer = new XmlTransformer()
    private final List<IvyConfiguration> configurations = new ArrayList<IvyConfiguration>()
    private final List<IvyArtifact> artifacts = new ArrayList<IvyArtifact>()

    IntelliJIvyDescriptorFileGenerator(IvyPublicationIdentity projectIdentity) {
        this.projectIdentity = projectIdentity
    }

    void addConfiguration(IvyConfiguration ivyConfiguration) {
        configurations.add(ivyConfiguration)
    }

    void addArtifact(IvyArtifact ivyArtifact) {
        artifacts.add(ivyArtifact)
    }

    void writeTo(File file) {
        xmlTransformer.transform(file, IVY_FILE_ENCODING, new Action<Writer>() {
            void execute(Writer writer) {
                try {
                    writeDescriptor(writer)
                } catch (IOException e) {
                    throw new UncheckedIOException(e)
                }
            }
        })
    }

    private void writeDescriptor(final Writer writer) throws IOException {
        OptionalAttributeXmlWriter xmlWriter = new OptionalAttributeXmlWriter(writer, "  ", IVY_FILE_ENCODING)
        xmlWriter.startElement("ivy-module").attribute("version", "2.0")
        if (usesClassifier()) {
            xmlWriter.attribute("xmlns:m", "http://ant.apache.org/ivy/maven")
        }
        xmlWriter.startElement("info")
                .attribute("organisation", projectIdentity.getOrganisation())
                .attribute("module", projectIdentity.getModule())
                .attribute("revision", projectIdentity.getRevision())
                .attribute("publication", ivyDateFormat.format(new Date()))
        xmlWriter.endElement()

        writeConfigurations(xmlWriter)
        writePublications(xmlWriter)
        xmlWriter.endElement()
    }

    private boolean usesClassifier() {
        for (IvyArtifact artifact : artifacts) {
            if (artifact.getClassifier() != null) {
                return true
            }
        }
        return false
    }

    private void writeConfigurations(OptionalAttributeXmlWriter xmlWriter) throws IOException {
        xmlWriter.startElement("configurations")
        for (IvyConfiguration configuration : configurations) {
            xmlWriter.startElement("conf")
                    .attribute("name", configuration.getName())
                    .attribute("visibility", "public")
            if (configuration.getExtends().size() > 0) {
                xmlWriter.attribute("extends", CollectionUtils.join(",", configuration.getExtends()))
            }
            xmlWriter.endElement()
        }
        xmlWriter.endElement()
    }

    private void writePublications(OptionalAttributeXmlWriter xmlWriter) throws IOException {
        xmlWriter.startElement("publications")
        for (IvyArtifact artifact : artifacts) {
            xmlWriter.startElement("artifact")
                    .attribute("name", artifact.getName())
                    .attribute("type", artifact.getType())
                    .attribute("ext", artifact.getExtension())
                    .attribute("conf", artifact.getConf())
                    .attribute("m:classifier", artifact.getClassifier())
                    .endElement()
        }
        xmlWriter.endElement()
    }

    private static class OptionalAttributeXmlWriter extends SimpleXmlWriter {
        OptionalAttributeXmlWriter(Writer writer, String indent, String encoding) throws IOException {
            super(writer, indent, encoding)
        }

        @Override
        OptionalAttributeXmlWriter startElement(String name) throws IOException {
            super.startElement(name)
            return this
        }

        @Override
        OptionalAttributeXmlWriter attribute(String name, String value) throws IOException {
            if (value != null) {
                super.attribute(name, value)
            }
            return this
        }
    }
}
