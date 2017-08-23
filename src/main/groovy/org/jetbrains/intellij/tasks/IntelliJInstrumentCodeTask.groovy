package org.jetbrains.intellij.tasks

import org.apache.tools.ant.BuildException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.*
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.dependency.IdeaDependency

class IntelliJInstrumentCodeTask extends ConventionTask {
    private static final String FILTER_ANNOTATION_REGEXP_CLASS = 'com.intellij.ant.ClassFilterAnnotationRegexp'
    private static final LOADER_REF = "java2.loader"

    SourceSet sourceSet

    @Input
    IdeaDependency ideaDependency

    @OutputDirectory
    File outputDir

    @InputFiles
    @SkipWhenEmpty
    FileTree getOriginalClasses() {
        def output = sourceSet.output
        return output.hasProperty("classesDirs") ?
                project.files(output.classesDirs.from).asFileTree :
                project.fileTree(output.classesDir)
    }

    @InputFiles
    FileCollection getSourceDirs() {
        return project.files(sourceSet.allSource.srcDirs.findAll { !sourceSet.resources.contains(it) && it.exists() })
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    @TaskAction
    void instrumentClasses() {
        def outputDir = getOutputDir()
        copyOriginalClasses(outputDir)

        def ideaDependency = getIdeaDependency()
        def classpath = project.files(
                "$ideaDependency.classes/lib/javac2.jar",
                "$ideaDependency.classes/lib/jdom.jar",
                "$ideaDependency.classes/lib/asm-all.jar",
                "$ideaDependency.classes/lib/jgoodies-forms.jar")

        ant.taskdef(name: 'instrumentIdeaExtensions',
                classpath: classpath.asPath,
                loaderref: LOADER_REF,
                classname: 'com.intellij.ant.InstrumentIdeaExtensions')

        logger.info("Compiling forms and instrumenting code with nullability preconditions")
        boolean instrumentNotNull = prepareNotNullInstrumenting(classpath)
        instrumentCode(getSourceDirs(), outputDir, instrumentNotNull)
    }


    private void copyOriginalClasses(@NotNull File outputDir) {
        outputDir.deleteDir()
        project.copy {
            from getOriginalClasses()
            into outputDir
        }
    }

    private boolean prepareNotNullInstrumenting(@NotNull ConfigurableFileCollection classpath) {
        try {
            ant.typedef(name: 'skip', classpath: classpath.asPath, loaderref: LOADER_REF,
                    classname: FILTER_ANNOTATION_REGEXP_CLASS)
        } catch (BuildException e) {
            def cause = e.getCause()
            if (cause instanceof ClassNotFoundException && FILTER_ANNOTATION_REGEXP_CLASS == cause.getMessage()) {
                logger.info("Old version of Javac2 is used, " +
                        "instrumenting code with nullability will be skipped. Use IDEA >14 SDK (139.*) to fix this")
                return false
            } else {
                throw e
            }
        }
        return true
    }

    private void instrumentCode(@NotNull FileCollection srcDirs, @NotNull File outputDir, boolean instrumentNotNull) {
        def headlessOldValue = System.setProperty('java.awt.headless', 'true')
        ant.instrumentIdeaExtensions(srcdir: srcDirs.asPath,
                destdir: outputDir, classpath: sourceSet.compileClasspath.asPath,
                includeantruntime: false, instrumentNotNull: instrumentNotNull) {
            if (instrumentNotNull) {
                ant.skip(pattern: 'kotlin/Metadata')
            }
        }
        if (headlessOldValue != null) {
            System.setProperty('java.awt.headless', headlessOldValue)
        } else {
            System.clearProperty('java.awt.headless')
        }
    }

}
