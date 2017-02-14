package org.jetbrains.intellij.tasks

import org.apache.tools.ant.BuildException
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.*
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.dependency.IdeaDependency

class IntelliJInstrumentCodeTask extends DefaultTask {
    private static final String FILTER_ANNOTATION_REGEXP_CLASS = 'com.intellij.ant.ClassFilterAnnotationRegexp'
    private static final LOADER_REF = "java2.loader"

    SourceSet sourceSet

    @Input
    IdeaDependency ideaDependency

    @OutputDirectory
    File output

    @InputFiles
    @SkipWhenEmpty
    FileTree getOriginalClasses() {
        return project.fileTree(sourceSet.output.classesDir)
    }

    @InputFiles
    FileCollection getSourceDirs() {
        def sourceDirs = existingDirs(sourceSet.allSource)
        sourceDirs.removeAll(sourceSet.resources)
        return project.files(sourceDirs)
    }

    @TaskAction
    void instrumentClasses() {
        copyOriginalClasses()

        def classpath = project.files(
                "$ideaDependency.classes/lib/javac2.jar",
                "$ideaDependency.classes/lib/jdom.jar",
                "$ideaDependency.classes/lib/asm-all.jar",
                "$ideaDependency.classes/lib/jgoodies-forms.jar")

        project.ant.taskdef(name: 'instrumentIdeaExtensions',
                classpath: classpath.asPath,
                loaderref: LOADER_REF,
                classname: 'com.intellij.ant.InstrumentIdeaExtensions')

        logger.info("Compiling forms and instrumenting code with nullability preconditions")
        boolean instrumentNotNull = prepareNotNullInstrumenting(classpath)
        instrumentCode(sourceDirs, instrumentNotNull)
    }

    private static HashSet<File> existingDirs(@NotNull SourceDirectorySet sourceDirectorySet) {
        return sourceDirectorySet.srcDirs.findAll { it.exists() }
    }

    private void copyOriginalClasses() {
        output.deleteDir()

        // Copy original classes
        project.copy {
            from originalClasses
            into output
        }
    }

    private boolean prepareNotNullInstrumenting(@NotNull ConfigurableFileCollection classpath) {
        try {
            project.ant.typedef(name: 'skip', classpath: classpath.asPath, loaderref: LOADER_REF,
                    classname: FILTER_ANNOTATION_REGEXP_CLASS)
        } catch (BuildException e) {
            def cause = e.getCause()
            if (cause instanceof ClassNotFoundException && FILTER_ANNOTATION_REGEXP_CLASS.equals(cause.getMessage())) {
                logger.info("Old version of Javac2 is used, " +
                        "instrumenting code with nullability will be skipped. Use IDEA >14 SDK (139.*) to fix this")
                return false
            } else {
                throw e
            }
        }
        return true
    }

    private void instrumentCode(@NotNull FileCollection srcDirs, boolean instrumentNotNull) {
        def headlessOldValue = System.setProperty('java.awt.headless', 'true')
        project.ant.instrumentIdeaExtensions(srcdir: srcDirs.asPath,
                destdir: output, classpath: sourceSet.compileClasspath.asPath,
                includeantruntime: false, instrumentNotNull: instrumentNotNull) {
            if (instrumentNotNull) {
                project.ant.skip(pattern: 'kotlin/Metadata')
            }
        }
        if (headlessOldValue != null) {
            System.setProperty('java.awt.headless', headlessOldValue)
        } else {
            System.clearProperty('java.awt.headless')
        }
    }

}
