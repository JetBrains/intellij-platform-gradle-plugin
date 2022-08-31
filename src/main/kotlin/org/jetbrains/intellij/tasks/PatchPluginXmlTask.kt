// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import org.gradle.api.Project
import org.gradle.api.internal.ConventionTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.jdom2.Document
import org.jdom2.Element
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.transformXml
import org.jetbrains.intellij.warn
import java.io.File
import javax.inject.Inject

open class PatchPluginXmlTask @Inject constructor(
    objectFactory: ObjectFactory,
) : ConventionTask() {

    /**
     * The directory where the patched plugin.xml will be written.
     *
     * Default value: `${project.buildDir}/patchedPluginXmlFiles`
     */
    @get:OutputDirectory
    val destinationDir = objectFactory.directoryProperty()

    /**
     * The list of output `plugin.xml` files.
     */
    @get:OutputFiles
    val outputFiles = objectFactory.listProperty<File>()

    /**
     * The list of `plugin.xml` files to patch.
     *
     * Default value: auto-discovered from the project
     */
    @get:SkipWhenEmpty
    @get:InputFiles
    val pluginXmlFiles = objectFactory.listProperty<File>()

    /**
     * The description of the plugin – will be set to the `<description>` tag.
     */
    @get:Input
    @get:Optional
    val pluginDescription = objectFactory.property<String>()

    /**
     * The lower bound of the version range to be patched – will be set for the `since-build` attribute of the `<idea-version>` tag.
     *
     * Default value: `intellij.version` in `Branch.Build.Fix` format
     */
    @get:Input
    @get:Optional
    val sinceBuild = objectFactory.property<String>()

    /**
     * The upper bound of the version range to be patched – will be set for the `until-build` attribute of the `<idea-version>` tag.
     *
     * Default value: `intellij.version` in `Branch.Build.*` format
     */
    @get:Input
    @get:Optional
    val untilBuild = objectFactory.property<String>()

    /**
     * The version of the plugin – will be set for the `<version>` tag.
     *
     * Default value: `${project.version}`
     */
    @get:Input
    @get:Optional
    val version = objectFactory.property<String>()

    /**
     * The change notes of the plugin – will be set for the `<change-notes>` tag.
     */
    @get:Input
    @get:Optional
    val changeNotes = objectFactory.property<String>()

    /**
     * The ID of the plugin – will be set for the `<id>` tag.
     */
    @get:Input
    @get:Optional
    val pluginId = objectFactory.property<String>()

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

                destinationDir.get().asFile.resolve(file.name).let {
                    transformXml(document, it)
                }
            }
        }
    }

    /**
     * Sets the [content] value of the given [document] to the given [name] tag.
     */
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

    /**
     * Sets the [attributeValue] value for the given [attributeName] of the given [document] under the specific [tagName] tag.
     */
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
