package org.jetbrains.intellij

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.annotations.NotNull

class IntelliJInstrumentCodeAction implements Action<Task> {
    @Override
    void execute(Task task) {
        def extension = task.project.extensions.getByType(IntelliJPluginExtension)
        def loader = "java2.loader"
        def classpath = task.project.files(
                "$extension.ideaDirectory/lib/javac2.jar",
                "$extension.ideaDirectory/lib/jdom.jar",
                "$extension.ideaDirectory/lib/asm-all.jar",
                "$extension.ideaDirectory/lib/jgoodies-forms.jar")
        task.project.ant.taskdef(name: 'instrumentIdeaExtensions',
                classpath: classpath.asPath,
                loaderref: loader,
                classname: 'com.intellij.ant.InstrumentIdeaExtensions')
        task.project.ant.typedef(name: 'skip',
                classpath: classpath.asPath,
                loaderref: loader,
                classname: 'com.intellij.ant.ClassFilterAnnotationRegexp')

        assert task instanceof AbstractCompile
        IntelliJPlugin.LOG.info("Compiling forms and instrumenting code with nullability preconditions")
        def srcDirs = Utils.mainSourceSet(task.project).compiledBy(task).java.srcDirs.findAll { it.exists() } +
                Utils.testSourceSet(task.project).compiledBy(task).java.srcDirs.findAll { it.exists() };
        if (!srcDirs.empty) {
            instrumentCode(task, srcDirs)
        }
    }

    private static void instrumentCode(@NotNull AbstractCompile compileTask, @NotNull Collection<File> srcDirs) {
        def headlessOldValue = System.setProperty('java.awt.headless', 'true')
        compileTask.project.ant.instrumentIdeaExtensions(srcdir: compileTask.project.files(srcDirs).asPath,
                destdir: compileTask.destinationDir, classpath: compileTask.classpath.asPath) {
            compileTask.project.ant.skip(pattern: 'kotlin/jvm/internal/.*')
        }
        if (headlessOldValue != null) {
            System.setProperty('java.awt.headless', headlessOldValue)
        } else {
            System.clearProperty('java.awt.headless')
        }
    }
}
