// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import com.dd.plist.NSArray
import com.dd.plist.NSDictionary
import com.dd.plist.NSObject
import com.dd.plist.NSString
import com.dd.plist.PropertyListParser
import com.jetbrains.plugin.structure.base.utils.listFiles
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

/**
 * Mounts and extracts DMG archive.
 */
abstract class DmgExtractorValueSource : ValueSource<Path, DmgExtractorValueSource.Parameters> {

    @get:Inject
    abstract val execOperations: ExecOperations

    interface Parameters : ValueSourceParameters {
        /**
         * Input dmg
         */
        val path: RegularFileProperty

        /**
         * Target
         */
        val target: DirectoryProperty
    }

    private val log = Logger(javaClass)
    private val tempDirectory = createTempDirectory()

    override fun obtain(): Path {
        val path = parameters.path.asPath
        val targetDirectory = parameters.target.asPath.createDirectories()

        if (targetDirectory.listFiles().isNotEmpty()) {
            log.info("Directory '$targetDirectory' already exists.")
            return targetDirectory
        }

        log.info("Extracting DMG archive '$path' to temporary directory.")

        val detachTarget = ByteArrayOutputStream().use { os ->
            execOperations.exec {
                commandLine("hdiutil", "info", "-plist")
                standardOutput = os
            }
            PropertyListParser.parse(os.toByteArray()).findDetachTarget(path.pathString)
        }

        detachTarget?.let { volume ->
            execOperations.exec {
                commandLine("hdiutil", "detach", "-force", "-quiet", volume)
            }
        }

        execOperations.exec {
            commandLine(
                "hdiutil",
                "attach",
                "-readonly",
                "-noautoopen",
                "-noautofsck",
                "-noverify",
                "-nobrowse",
                "-mountpoint",
                "-quiet",
                tempDirectory,
                path.safePathString,
            )
        }

        execOperations.exec {
            commandLine(
                "rsync",
                "-av",
                "--quiet",
                "--exclude=Applications",
                "--exclude=/.*",
                "$tempDirectory/",
                targetDirectory,
            )
            isIgnoreExitValue = true
        }

        log.info("Unmounting DMG volume '$tempDirectory'.")
        execOperations.exec {
            commandLine("hdiutil", "detach", "-force", "-quiet", tempDirectory)
        }

        return targetDirectory
    }
}

internal fun NSObject?.findDetachTarget(imagePath: String): String? {
    val image = (this as? NSDictionary)
        ?.dictionaries("images")
        ?.firstOrNull { it.string("image-path") == imagePath }
        ?: return null
    val systemEntities = image.dictionaries("system-entities")
    val mountedEntity = systemEntities.firstOrNull { it.string("mount-point") != null }

    return mountedEntity?.string("dev-entry")
        ?: mountedEntity?.string("mount-point")
        ?: systemEntities.firstNotNullOfOrNull { it.string("dev-entry") }
}

private fun NSDictionary.dictionaries(key: String) =
    (objectForKey(key) as? NSArray)
        ?.array
        ?.filterIsInstance<NSDictionary>()
        .orEmpty()

private fun NSDictionary.string(key: String) =
    (objectForKey(key) as? NSString)?.content
