package org.jetbrains.intellij

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.archive.ZipCopyAction
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.DefaultZipCompressor
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.AbstractArchiveTask

import java.util.zip.ZipOutputStream

class BuildPluginTask extends AbstractArchiveTask {
    public static final String NAME = "buildPlugin"
    Collection<File> filesToIgnore

    BuildPluginTask() {
        super()
        setExtension("zip")
        name = NAME
        destinationDir = project.jar.destinationDir
        description = "Build intellij plugin artifact"
        group = "intellij"
        baseName = project.name
    }

    public void configure() {
        from({
            def files = new HashSet<>()
            project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).each {
                if (!filesToIgnore.contains(it)) files.add(it)
            }
            if (!files.isEmpty()) files.add(project.jar)
            files
        }).into("${baseName}/lib")
    }

    @Override
    protected CopyAction createCopyAction() {
        DocumentationRegistry documentationRegistry = getServices().get(DocumentationRegistry.class);
        return new ZipCopyAction(getArchivePath(), new DefaultZipCompressor(false, ZipOutputStream.DEFLATED), documentationRegistry);
    }
}
