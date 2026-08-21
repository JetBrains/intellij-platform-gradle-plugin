// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jdom2.Element
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.Variant
import org.jetbrains.intellij.platform.gradle.models.transformXml
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import org.jetbrains.intellij.platform.gradle.variants
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes

private const val PLUGIN_XML_PATH = "META-INF/plugin.xml"

/**
 * Creates an OS- and architecture-specific plugin Jar from the shared composed plugin Jar.
 *
 * Native variant compatibility must start at IntelliJ Platform 2026.1 (build 261). The resulting plugin descriptor
 * must declare `since-build` 261 or later because the injected OS and architecture module dependencies are unavailable
 * in earlier platform versions.
 */
@CacheableTask
abstract class PreparePluginVariantTask : DefaultTask() {

    private val sinceBuild = project.objects.property(String::class.java)

    /**
     * The shared composed plugin Jar used as the source of the variant.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputJar: RegularFileProperty

    /**
     * The plugin version written to the variant's `plugin.xml` file.
     */
    @get:Input
    abstract val pluginVersion: Property<String>

    /**
     * The operating-system suffix of the required IntelliJ Platform module.
     */
    @get:Input
    abstract val operatingSystem: Property<String>

    /**
     * The architecture suffix of the required IntelliJ Platform module.
     */
    @get:Input
    abstract val architecture: Property<String>

    /**
     * The directory containing the variant-specific plugin Jar.
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = Plugin.GROUP_NAME
        description = "Creates an OS- and architecture-specific plugin Jar."
        inputs.property("sinceBuild", sinceBuild.orElse(""))
    }

    @TaskAction
    fun preparePluginVariant() {
        validateSinceBuild()

        val outputPath = outputDirectory.asPath
            .also { it.toFile().deleteRecursively() }
            .also { it.createDirectories() }
            .resolve(inputJar.asPath.fileName)
        val temporaryPath = outputPath.resolveSibling("${outputPath.fileName}.tmp")

        try {
            ZipFile(inputJar.asFile.get()).use { input ->
                ZipOutputStream(temporaryPath.outputStream().buffered()).use { output ->
                    input.entries().asSequence().forEach { entry ->
                        output.putNextEntry(ZipEntry(entry.name).apply { time = 0L })

                        if (!entry.isDirectory) {
                            input.getInputStream(entry).use { content ->
                                when (entry.name) {
                                    PLUGIN_XML_PATH -> output.write(patchPluginXml(content.readBytes()))
                                    else -> content.copyTo(output)
                                }
                            }
                        }

                        output.closeEntry()
                    }
                }
            }

            Files.move(temporaryPath, outputPath, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporaryPath.deleteIfExists()
        }
    }

    private fun patchPluginXml(content: ByteArray): ByteArray {
        val document = JDOMUtil.loadDocument(content.inputStream())
        val pluginXml = requireNotNull(document.rootElement.takeIf { it.name == "idea-plugin" }) {
            "Invalid '$PLUGIN_XML_PATH': '<idea-plugin>' root element was expected."
        }

        val version = pluginXml.getChild("version") ?: Element("version").also {
            pluginXml.addContent(0, it)
        }
        version.text = pluginVersion.get()

        listOf(
            "com.intellij.modules.os.${operatingSystem.get()}",
            "com.intellij.modules.arch.${architecture.get()}",
        ).forEach { dependency ->
            if (pluginXml.getChildren("depends").none { it.textTrim == dependency }) {
                pluginXml.addContent(Element("depends").setText(dependency))
            }
        }

        return temporaryDir.toPath()
            .resolve("plugin.xml")
            .also { transformXml(document, it) }
            .readBytes()
    }

    private fun validateSinceBuild() {
        val configuredSinceBuild = sinceBuild.orNull

        require(configuredSinceBuild?.toVersion()?.let { it >= Constraints.MINIMAL_NATIVE_VARIANTS_BUILD_NUMBER } == true) {
            "The `nativeVariants` feature requires `since-build` " +
                "${Constraints.MINIMAL_NATIVE_VARIANTS_BUILD_NUMBER} " +
                "(IntelliJ Platform ${Constraints.MINIMAL_NATIVE_VARIANTS_VERSION}) or later, but " +
                "'${configuredSinceBuild ?: "<unspecified>"}' was provided."
        }
    }

    companion object : Registrable {
        internal fun taskName(variant: Variant) =
            with(variant) { "${Tasks.PREPARE_PLUGIN_VARIANT}_${os}_$arch" }

        override fun register(project: Project) {
            val composedJarTaskProvider = project.tasks.named<ComposedJarTask>(Tasks.COMPOSED_JAR)
            val patchPluginXmlTaskProvider = project.tasks.named<PatchPluginXmlTask>(Tasks.PATCH_PLUGIN_XML)
            val pluginVersionProvider = project.extensionProvider.flatMap { it.pluginConfiguration.version }
            val nativeVariantsEnabledProvider = project.extensionProvider.flatMap { it.nativeVariants.enabled }

            variants.forEach { variant ->
                val (os, arch) = variant

                project.tasks.register<PreparePluginVariantTask>(taskName(variant)) {
                    inputJar.convention(composedJarTaskProvider.flatMap { it.archiveFile })
                    sinceBuild.convention(patchPluginXmlTaskProvider.flatMap { it.sinceBuild })
                    pluginVersion.convention(pluginVersionProvider.map { "$it-$os-$arch" })
                    operatingSystem.convention(os)
                    architecture.convention(arch)
                    outputDirectory.convention(project.layout.buildDirectory.dir("intermediates/pluginVariants/$os-$arch"))

                    onlyIf("native variants are enabled") {
                        nativeVariantsEnabledProvider.get()
                    }
                }
            }
        }
    }
}
