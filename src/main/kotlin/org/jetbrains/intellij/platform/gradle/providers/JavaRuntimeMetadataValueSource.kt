// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.models.launchFor
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.io.path.pathString

/**
 * Obtains the architecture of the provided Java Runtime executable by requesting the list of its internal properties.
 *
 * It is used to properly pick the [ProductInfo.Launch] when calling the [ProductInfo.launchFor] helper method.
 */
abstract class JavaRuntimeMetadataValueSource : ValueSource<Map<String, String>, JavaRuntimeMetadataValueSource.Parameters> {

    @get:Inject
    abstract val execOperations: ExecOperations

    interface Parameters : ValueSourceParameters {
        /**
         * Java Runtime executable.
         */
        val executable: RegularFileProperty
    }

    /**
     * Sample output:
     * ```
     * user@user-kubuntu:~ $ /opt/ideau/jbr/bin/java -XshowSettings:properties -version
     * Property settings:
     *     awt.toolkit.name = XToolkit
     *     file.encoding = UTF-8
     *     file.separator = /
     *     intellij.os.virtualization = none
     *     java.class.path =
     *     java.class.version = 65.0
     *     java.home = /opt/ideau/jbr
     *     java.io.tmpdir = /tmp
     *     java.library.path = /usr/java/packages/lib
     *         /usr/lib64
     *         /lib64
     *         /lib
     *         /usr/lib
     *     java.runtime.name = OpenJDK Runtime Environment
     *     java.runtime.version = 21.0.4+13-b509.17
     *     java.specification.name = Java Platform API Specification
     *     java.specification.vendor = Oracle Corporation
     *     java.specification.version = 21
     *     java.vendor = JetBrains s.r.o.
     *     java.vendor.url = https://openjdk.org/
     *     java.vendor.url.bug = https://bugreport.java.com/bugreport/
     *     java.vendor.version = JBR-21.0.4+13-509.17-jcef
     *     java.version = 21.0.4
     *     java.version.date = 2024-07-16
     *     java.vm.compressedOopsMode = Zero based
     *     java.vm.info = mixed mode
     *     java.vm.name = OpenJDK 64-Bit Server VM
     *     java.vm.specification.name = Java Virtual Machine Specification
     *     java.vm.specification.vendor = Oracle Corporation
     *     java.vm.specification.version = 21
     *     java.vm.vendor = JetBrains s.r.o.
     *     java.vm.version = 21.0.4+13-b509.17
     *     jbr.virtualization.information = No virtualization detected
     *     jdk.debug = release
     *     line.separator = \n
     *     native.encoding = UTF-8
     *     os.arch = amd64
     *     os.name = Linux
     *     os.version = 6.8.0-47-generic
     *     path.separator = :
     *     stderr.encoding = UTF-8
     *     stdout.encoding = UTF-8
     *     sun.arch.data.model = 64
     *     sun.boot.library.path = /opt/ideau/jbr/lib
     *     sun.cpu.endian = little
     *     sun.io.unicode.encoding = UnicodeLittle
     *     sun.java.launcher = SUN_STANDARD
     *     sun.jnu.encoding = UTF-8
     *     sun.management.compiler = HotSpot 64-Bit Tiered Compilers
     *     user.country = GB
     *     user.dir = /home/sasha
     *     user.home = /home/sasha
     *     user.language = en
     *     user.name = sasha
     *
     * openjdk version "21.0.4" 2024-07-16
     * OpenJDK Runtime Environment JBR-21.0.4+13-509.17-jcef (build 21.0.4+13-b509.17)
     * OpenJDK 64-Bit Server VM JBR-21.0.4+13-509.17-jcef (build 21.0.4+13-b509.17, mixed mode)
     * ```
     */
    override fun obtain() = ByteArrayOutputStream().use { os ->
        execOperations.exec {
            commandLine(
                parameters.executable.asPath.pathString,
                "-XshowSettings:properties",
                "-version",
            )
            errorOutput = os
        }

        val separator = " = "
        os.toString()
            .lines()
            .dropWhile { !it.contains(separator) }
            .dropLastWhile { !it.contains(separator) }
            .joinToString("\n")
            .trimIndent()
            .replace("\n +".toRegex(), ",")
            .lines()
            .filter { it.contains(separator) }
            .associate { it.split(separator).let { (key, value) -> key to value } }
    }
}
