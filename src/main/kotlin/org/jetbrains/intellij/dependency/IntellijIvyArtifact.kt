// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.publish.ivy.IvyArtifact
import java.io.File

internal class IntellijIvyArtifact(
    internal val file: File,
    internal var name: String,
    private var extension: String,
    internal var type: String,
    private var classifier: String?,
) : IvyArtifact {

    private var configuration: String? = null
    private val buildDependencies = DefaultTaskDependency()

    companion object {
        fun createJarDependency(file: File, configuration: String, baseDir: File, classifier: String? = null) =
            createDependency(baseDir, file, configuration, "jar", "jar", classifier)

        fun createZipDependency(file: File, configuration: String, baseDir: File, classifier: String? = null) =
            createDependency(baseDir, file, configuration, "zip", "zip", classifier)

        fun createDirectoryDependency(file: File, configuration: String, baseDir: File, classifier: String? = null) =
            createDependency(baseDir, file, configuration, "", "directory", classifier)

        private fun createDependency(
            baseDir: File,
            file: File,
            configuration: String,
            extension: String,
            type: String,
            classifier: String?,
        ): IvyArtifact {
            val relativePath = baseDir.toURI().relativize(file.toURI()).path
            val name = relativePath.removeSuffix(".$extension")
            return IntellijIvyArtifact(file, name, extension, type, classifier).apply {
                this.configuration = configuration
            }
        }
    }

    override fun getBuildDependencies() = buildDependencies

    override fun getFile() = file

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
}
