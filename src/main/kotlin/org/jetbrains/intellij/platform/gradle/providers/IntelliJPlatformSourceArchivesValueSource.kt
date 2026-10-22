// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.Path

/** Gradle's HTTP artifact cache files are not automatically configuration-cache inputs. */
internal abstract class IntelliJPlatformSourceArchivesValueSource : ValueSource<List<String>, IntelliJPlatformSourceArchivesValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val archives: ListProperty<String>
    }

    override fun obtain(): List<String> = parameters.archives.get().map { archive ->
        runCatching {
            val attributes = Files.readAttributes(Path(archive), BasicFileAttributes::class.java)
            "${attributes.isRegularFile}:${attributes.size()}:${attributes.lastModifiedTime()}"
        }.getOrDefault("unavailable")
    }
}