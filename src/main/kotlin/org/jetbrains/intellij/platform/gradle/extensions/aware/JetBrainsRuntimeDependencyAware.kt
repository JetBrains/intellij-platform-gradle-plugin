// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.aware

import org.gradle.api.GradleException
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.extensions.DependencyAction
import java.io.FileReader
import java.util.*
import kotlin.io.path.exists

interface JetBrainsRuntimeDependencyAware : IntelliJPlatformAware, DependencyAware {
    val providers: ProviderFactory
}

/**
 * A base method for adding a dependency on JetBrains Runtime.
 *
 * @param configurationName The name of the configuration to add the dependency to.
 * @param action The action to be performed on the dependency. Defaults to an empty action.
 */
internal fun JetBrainsRuntimeDependencyAware.addObtainedJetBrainsRuntimeDependency(
    configurationName: String = Configurations.JETBRAINS_RUNTIME_DEPENDENCY,
    action: DependencyAction = {},
) = addJetBrainsRuntimeDependency(
    providers.provider {
        val version = platformPath.resolve("dependencies.txt")
            .takeIf { it.exists() }
            ?.let {
                FileReader(it.toFile()).use { reader ->
                    with(Properties()) {
                        load(reader)
                        getProperty("runtimeBuild") ?: getProperty("jdkBuild")
                    }
                }
            } ?: throw GradleException("Could not obtain JetBrains Runtime version with the current IntelliJ Platform.")

        buildJetBrainsRuntimeVersion(version)
    },
    configurationName,
    action,
)

/**
 * A base method for adding a dependency on JetBrains Runtime.
 *
 * @param explicitVersionProvider The provider for the explicit version of the JetBrains Runtime.
 * @param configurationName The name of the configuration to add the dependency to.
 * @param action The action to be performed on the dependency. Defaults to an empty action.
 */
internal fun JetBrainsRuntimeDependencyAware.addJetBrainsRuntimeDependency(
    explicitVersionProvider: Provider<String>,
    configurationName: String = Configurations.JETBRAINS_RUNTIME_DEPENDENCY,
    action: DependencyAction = {},
) = configurations[configurationName].dependencies.addLater(
    explicitVersionProvider.map {
        dependencies.create(
            group = "com.jetbrains",
            name = "jbr",
            version = it,
            ext = "tar.gz",
        ).apply(action)
    },
)

internal fun buildJetBrainsRuntimeVersion(
    version: String,
    runtimeVariant: String? = null,
    architecture: String? = null,
    operatingSystem: OperatingSystem = OperatingSystem.current(),
): String {
    val variant = runtimeVariant ?: "jcef"

    val (jdk, build) = version.split('b').also {
        assert(it.size == 1) {
            "Incorrect JetBrains Runtime version: $version. Use [sdk]b[build] format, like: 21.0.3b446.1"
        }
    }

    val os = with(operatingSystem) {
        when {
            isWindows -> "windows"
            isMacOsX -> "osx"
            isLinux -> "linux"
            else -> throw GradleException("Unsupported operating system: $name")
        }
    }

    val arch = when (architecture ?: System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "aarch64"
        "x86_64", "amd64" -> "x64"
        else -> when {
            operatingSystem.isWindows && System.getenv("ProgramFiles(x86)") != null -> "x64"
            else -> "x86"
        }
    }

    return "jbr_$variant-$jdk-$os-$arch-b$build"
}
