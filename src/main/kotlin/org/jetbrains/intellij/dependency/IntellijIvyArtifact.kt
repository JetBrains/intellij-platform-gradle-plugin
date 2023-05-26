// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.publish.ivy.IvyArtifact
import java.io.File
import java.nio.file.Path

class IntellijIvyArtifact(
    internal val file: Path,
    internal var name: String,
    private var extension: String,
    internal var type: String,
    private var classifier: String?,
) : IvyArtifact {

    private var configuration: String? = null
    private val buildDependencies = DefaultTaskDependency()

    override fun getBuildDependencies() = buildDependencies

    override fun getFile(): File = file.toFile()

    override fun builtBy(vararg tasks: Any) {
        buildDependencies.add(tasks)
    }

    override fun getName() = name

    override fun setName(name: String) {
        this.name = name
    }

    override fun getType() = type

    override fun setType(type: String) {
        this.type = type
    }

    override fun getExtension() = extension

    override fun setExtension(extension: String) {
        this.extension = extension
    }

    override fun getClassifier() = classifier

    override fun setClassifier(classifier: String?) {
        this.classifier = classifier
    }

    override fun getConf() = configuration

    override fun setConf(conf: String?) {
        this.configuration = conf
    }

    override fun toString() = "IntellijIvyArtifact $name:$type:$extension:$classifier"

    companion object {
        fun createJarDependency(file: Path, configuration: String, baseDir: Path, classifier: String? = null) =
            createDependency(baseDir, file, configuration, "jar", "jar", classifier)

        fun createZipDependency(file: Path, configuration: String, baseDir: Path, classifier: String? = null) =
            createDependency(baseDir, file, configuration, "zip", "zip", classifier)

        fun createDirectoryDependency(file: Path, configuration: String, baseDir: Path, classifier: String? = null) =
            createDependency(baseDir, file, configuration, "", "directory", classifier)

        private fun createDependency(
            baseDir: Path,
            file: Path,
            configuration: String,
            extension: String,
            type: String,
            classifier: String?,
        ): IvyArtifact {
            val relativePath = baseDir.toUri().relativize(file.toUri()).path
            val name = relativePath.removeSuffix(".$extension")
            return IntellijIvyArtifact(file, name, extension, type, classifier).apply {
                this.configuration = configuration
            }
        }
    }
}
