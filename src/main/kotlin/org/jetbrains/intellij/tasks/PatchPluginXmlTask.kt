package org.jetbrains.intellij.tasks

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.ConventionTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.jetbrains.intellij.asSequence
import org.jetbrains.intellij.attribute
import org.jetbrains.intellij.get
import org.jetbrains.intellij.warn
import org.w3c.dom.Document
import java.io.File
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

@Suppress("UnstableApiUsage")
open class PatchPluginXmlTask @Inject constructor(
    objectFactory: ObjectFactory,
) : ConventionTask() {

    @OutputDirectory
    val destinationDir: DirectoryProperty = objectFactory.directoryProperty()

    @SkipWhenEmpty
    @InputFiles
    val pluginXmlFiles: ListProperty<File> = objectFactory.listProperty(File::class.java)

    @Input
    @Optional
    val pluginDescription: Property<String> = objectFactory.property(String::class.java)

    @Input
    @Optional
    val sinceBuild: Property<String> = objectFactory.property(String::class.java)

    @Input
    @Optional
    val untilBuild: Property<String> = objectFactory.property(String::class.java)

    @Input
    @Optional
    val version: Property<String> = objectFactory.property(String::class.java)

    @Input
    @Optional
    val changeNotes: Property<String> = objectFactory.property(String::class.java)

    @Input
    @Optional
    val pluginId: Property<String> = objectFactory.property(String::class.java)

    @TaskAction
    fun patchPluginXmlFiles() {
        pluginXmlFiles.get().forEach { file ->
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(file)

            sinceBuild.orNull?.let {
                patchAttribute(document, "idea-version", "since-build", it)
            }
            untilBuild.orNull?.let {
                patchAttribute(document, "idea-version", "until-build", it)
            }
            pluginDescription.orNull?.let {
                patchTag(document, "description", it)
            }
            changeNotes.orNull?.let {
                patchTag(document, "change-notes", it)
            }
            version.orNull.takeIf { it != Project.DEFAULT_VERSION }?.let {
                patchTag(document, "version", it)
            }
            pluginId.orNull?.let {
                patchTag(document, "id", it)
            }

            val destination = File(destinationDir.get().asFile, file.name)

            TransformerFactory.newInstance()
                .newTransformer()
                .apply {
                    setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                    setOutputProperty(OutputKeys.INDENT, "yes")
                    setOutputProperty(OutputKeys.METHOD, "xml")
                    setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
                    setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
                }
                .transform(DOMSource(document), StreamResult(destination))
        }
    }

    private fun patchTag(document: Document, name: String, content: String) {
        if (content.isEmpty()) {
            return
        }
        val pluginXml = document.childNodes.asSequence().find { it.nodeName == "idea-plugin" } ?: return

        val tag = pluginXml.get(name)
        if (tag != null) {
            val existingValue = tag.textContent
            if (existingValue.isNotEmpty()) {
                warn(this, "Patching plugin.xml: value of `$name[$existingValue]` tag will be set to `$content`")
            }
            tag.textContent = content
        } else {
            pluginXml.insertBefore(
                document.createElement(name).apply {
                    textContent = content
                },
                pluginXml.firstChild,
            )
        }
    }

    private fun patchAttribute(document: Document, tagName: String, attributeName: String, attributeValue: String) {
        if (attributeValue.isEmpty()) {
            return
        }
        val pluginXml = document.childNodes.asSequence().find { it.nodeName == "idea-plugin" } ?: return

        val tag = pluginXml.get(tagName)
        if (tag != null) {
            val existingValue = tag.attribute(attributeName)
            if (!existingValue.isNullOrEmpty()) {
                warn(this, "Patching plugin.xml: attribute `$attributeName=[$existingValue]` of `$tagName` tag will be set to `$attributeValue`")
            }
            tag.attributes.setNamedItem(
                document.createAttribute(attributeName).apply {
                    textContent = attributeValue
                }
            )
        } else {
            pluginXml.insertBefore(
                document.createElement(tagName).apply {
                    setAttribute(attributeName, attributeValue)
                },
                pluginXml.firstChild,
            )
        }
    }
}
