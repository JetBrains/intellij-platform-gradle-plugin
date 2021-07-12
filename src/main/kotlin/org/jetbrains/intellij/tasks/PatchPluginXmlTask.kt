package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
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
import org.jdom2.Document
import org.jdom2.Element
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.transformXml
import org.jetbrains.intellij.warn
import java.io.File
import javax.inject.Inject

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

    private val context = logCategory()

    @TaskAction
    fun patchPluginXmlFiles() {
        pluginXmlFiles.get().forEach { file ->
            file.inputStream().use { inputStream ->
                val document = JDOMUtil.loadDocument(inputStream)

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
                transformXml(document, destination)
            }
        }
    }

    private fun patchTag(document: Document, name: String, content: String) {
        if (content.isEmpty()) {
            return
        }
        val pluginXml = document.rootElement.takeIf { it.name == "idea-plugin" } ?: return

        val tag = pluginXml.getChild(name)
        if (tag != null) {
            val existingValue = tag.text
            if (existingValue.isNotEmpty()) {
                warn(context, "Patching plugin.xml: value of '$name[$existingValue]' tag will be set to '$content'")
            }
            tag.text = content
        } else {
            pluginXml.addContent(0, Element(name).apply { text = content })
        }
    }

    private fun patchAttribute(document: Document, tagName: String, attributeName: String, attributeValue: String) {
        if (attributeValue.isEmpty()) {
            return
        }
        val pluginXml = document.rootElement.takeIf { it.name == "idea-plugin" } ?: return

        val tag = pluginXml.getChild(tagName)
        if (tag != null) {
            val existingValue = tag.getAttribute(attributeName)?.value
            if (!existingValue.isNullOrEmpty()) {
                warn(context,
                    "Patching plugin.xml: attribute '$attributeName=[$existingValue]' of '$tagName' tag will be set to '$attributeValue'")
            }
            tag.setAttribute(attributeName, attributeValue)
        } else {
            pluginXml.addContent(0, Element(tagName).apply { setAttribute(attributeName, attributeValue) })
        }
    }
}
