package org.jetbrains.intellij.tasks

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.gradle.tooling.BuildException
import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.dependency.IdeaDependency
import org.jetbrains.intellij.releaseType
import java.io.File
import java.net.URI

@Suppress("UnstableApiUsage")
open class IntelliJInstrumentCodeTask : ConventionTask() {

    companion object {
        const val FILTER_ANNOTATION_REGEXP_CLASS = "com.intellij.ant.ClassFilterAnnotationRegexp"
        const val LOADER_REF = "java2.loader"
        const val ASM_REPOSITORY_URL = "https://cache-redirector.jetbrains.com/intellij-dependencies"
        const val FORMS_REPOSITORY_URL = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2"
    }

    private val extension = project.extensions.findByType(IntelliJPluginExtension::class.java)

    @Internal
    val sourceSet: Property<SourceSet> = project.objects.property(SourceSet::class.java)

    @Input
    @Optional
    val ideaDependency: Property<IdeaDependency> = project.objects.property(IdeaDependency::class.java)

    @InputFile
    @Optional
    val javac2: RegularFileProperty = project.objects.fileProperty()

    @Input
    val compilerVersion: Property<String> = project.objects.property(String::class.java)

    @OutputDirectory
    val outputDir: DirectoryProperty = project.objects.directoryProperty()

    @InputFiles
    @SkipWhenEmpty
    fun getOriginalClasses(): FileTree = sourceSet.get().output.classesDirs.let {
        if (it is DefaultConfigurableFileCollection) {
            project.files(it.from).asFileTree
        } else {
            project.fileTree(it)
        }
    }

    @InputFiles
    fun getSourceDirs(): FileCollection =
        project.files(sourceSet.get().allSource.srcDirs.filter { !sourceSet.get().resources.contains(it) && it.exists() })

    @TaskAction
    fun instrumentClasses() {
        copyOriginalClasses(outputDir.get().asFile)

        val classpath = compilerClassPath()

        ant.invokeMethod("taskdef", mapOf(
            "name" to "instrumentIdeaExtensions",
            "classpath" to classpath.asPath,
            "loaderref" to LOADER_REF,
            "classname" to "com.intellij.ant.InstrumentIdeaExtensions",
        ))

        logger.info("Compiling forms and instrumenting code with nullability preconditions")
        val instrumentNotNull = prepareNotNullInstrumenting(classpath)
        instrumentCode(getSourceDirs(), outputDir.get().asFile, instrumentNotNull)
    }

    private fun compilerClassPath(): FileCollection {
        // local compiler
        if (javac2.get().asFile.exists()) {
            return project.files(
                javac2,
                project.fileTree("${ideaDependency.get().classes}/lib").include(
                    "jdom.jar",
                    "asm-all.jar",
                    "asm-all-*.jar",
                    "jgoodies-forms.jar",
                    "forms-*.jar",
                )
            )
        }

        return compilerClassPathFromMaven()
    }

    private fun compilerClassPathFromMaven(): ConfigurableFileCollection {
        val dependency = project.dependencies.create("com.jetbrains.intellij.java:java-compiler-ant-tasks:${compilerVersion.get()}")
        val intellijRepositoryUrl = extension?.intellijRepository ?: IntelliJPluginConstants.DEFAULT_INTELLIJ_REPOSITORY
        val repos = listOf(
            project.repositories.maven { it.url = URI("$intellijRepositoryUrl/${releaseType(compilerVersion.get())}") },
            project.repositories.maven { it.url = URI(ASM_REPOSITORY_URL) },
            project.repositories.maven { it.url = URI(FORMS_REPOSITORY_URL) },
        )
        try {
            return project.files(project.configurations.detachedConfiguration(dependency).files)
        } finally {
            project.repositories.removeAll(repos)
        }
    }

    private fun copyOriginalClasses(outputDir: File) {
        outputDir.deleteRecursively()
        project.copy {
            it.from(getOriginalClasses())
            it.into(outputDir)
        }
    }

    private fun prepareNotNullInstrumenting(classpath: FileCollection): Boolean {
        try {
            ant.invokeMethod("typedef", mapOf(
                "name" to "skip",
                "classpath" to classpath.asPath,
                "loaderref" to LOADER_REF,
                "classname" to FILTER_ANNOTATION_REGEXP_CLASS,
            ))
        } catch (e: BuildException) {
            val cause = e.cause
            if (cause is ClassNotFoundException && FILTER_ANNOTATION_REGEXP_CLASS == cause.message) {
                logger.info("Old version of Javac2 is used, " +
                    "instrumenting code with nullability will be skipped. Use IDEA >14 SDK (139.*) to fix this")
                return false
            } else {
                throw e
            }
        }
        return true
    }

    private fun instrumentCode(srcDirs: FileCollection, outputDir: File, instrumentNotNull: Boolean) {
        val headlessOldValue = System.setProperty("java.awt.headless", "true")
        ant.invokeMethod("instrumentIdeaExtensions", mapOf(
            "srcdir" to srcDirs.asPath,
            "destdir" to outputDir,
            "classpath" to sourceSet.get().compileClasspath.asPath,
            "includeantruntime" to false,
            "instrumentNotNull" to instrumentNotNull,
        ))

        if (instrumentNotNull) {
            ant.invokeMethod("skip", mapOf(
                "pattern" to "kotlin/Metadata"
            ))
        }

        if (headlessOldValue != null) {
            System.setProperty("java.awt.headless", headlessOldValue)
        } else {
            System.clearProperty("java.awt.headless")
        }
    }
}
