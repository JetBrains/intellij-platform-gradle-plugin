package org.jetbrains.intellij.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.process.ExecOperations
import org.jetbrains.intellij.utils.ExternalToolRunner
import javax.inject.Inject

// TODO: AbstractTask from Compose plugin. Keep this or merge into AbstractProguardTask?
abstract class AbstractTask : DefaultTask() {
    @get:Inject
    protected abstract val objects: ObjectFactory

    @get:Inject
    protected abstract val providers: ProviderFactory
    @get:Internal
    val verbose: Property<Boolean> = objects.property(Boolean::class.java).apply {
        set(providers.provider {
            logger.isDebugEnabled
        })
    }

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @get:Inject
    protected abstract val fileOperations: FileOperations

    @get:LocalState
    protected val logsDir: Provider<Directory> = project.layout.buildDirectory.dir("intellij/logs/$name")

    @get:Internal
    internal val runExternalTool: ExternalToolRunner
        get() = ExternalToolRunner(verbose, logsDir, execOperations)

    protected fun cleanDirs(vararg dirs: Provider<out FileSystemLocation>) {
        for (dir in dirs) {
            fileOperations.delete(dir)
            fileOperations.mkdir(dir)
        }
    }
}