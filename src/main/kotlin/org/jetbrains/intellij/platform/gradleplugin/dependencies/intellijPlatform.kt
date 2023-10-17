// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("unused")

package org.jetbrains.intellij.platform.gradleplugin.dependencies

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.create
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPlatformType.*
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations

internal fun DependencyHandler.intellijPlatform(
    type: IntelliJPlatformType,
    version: String,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
) = add(configurationName, create(type, version))

internal fun DependencyHandler.intellijPlatform(
    type: IntelliJPlatformType,
    versionProvider: Provider<String>,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
) = addProvider(configurationName, versionProvider.map { version -> create(type, version) })

internal fun DependencyHandler.intellijPlatform(
    typeProvider: Provider<IntelliJPlatformType>,
    version: String,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
) = addProvider(configurationName, typeProvider.map { type -> create(type, version) })

internal fun DependencyHandler.intellijPlatform(
    typeProvider: Provider<IntelliJPlatformType>,
    versionProvider: Provider<String>,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
) = addProvider(configurationName, typeProvider.zip(versionProvider) { type, version -> create(type, version) })

internal fun DependencyHandler.create(
    type: IntelliJPlatformType,
    version: String,
    settings: IntelliJPlatformDependencySettings = intellijPlatformDependencySettings,
    action: DependencyAction = {},
) = create(
    group = type.groupId,
    name = type.artifactId,
    version = version,
).apply {
    action(this, settings)
}

//    return when (type) {
//        IntellijIdeaUltimate -> create(
//            group = "com.jetbrains.intellij.idea",
//            name = "ideaIU",
//            version = version,
//        )
//
//        CLion -> create(
//            group = "com.jetbrains.intellij.clion",
//            name = "clion",
//            version = version,
//        )
//
//        PyCharmProfessional -> create(
//            group = "com.jetbrains.intellij.pycharm",
//            name = "pycharmPY",
//            version = version,
//        )
//
//        PyCharmCommunity -> create(
//            group = "com.jetbrains.intellij.pycharm",
//            name = "pycharmPC",
//            version = version,
//        )
//
//        GoLand -> create(
//            group = "com.jetbrains.intellij.goland",
//            name = "goland",
//            version = version,
//        )
//
//        PhpStorm -> create(
//            group = "com.jetbrains.intellij.phpstorm",
//            name = "phpstorm",
//            version = version,
//        )
//
//        Rider -> create(
//            group = "com.jetbrains.intellij.rider",
//            name = "riderRD",
//            version = version,
//        )
//
//        Gateway -> create(
//            group = "com.jetbrains.gateway",
//            name = "JetBrainsGateway",
//            version = version,
//        )
//
//        AndroidStudio -> create(
//            group = "com.google.android.studio",
//            name = "android-studio",
//            version = version,
//            ext = when {
//                OperatingSystem.current().isLinux -> "tar.gz"
//                else -> "zip"
//            },
//        )
//    {
//        with(it) {
//            Files.list(resolveAndroidStudioPath(this))
//                .forEach { entry -> Files.move(entry, resolve(entry.fileName), StandardCopyOption.REPLACE_EXISTING) }
//        }
//    }
//    }

fun DependencyHandler.intellijPlatform(type: String, version: String) = intellijPlatform(IntelliJPlatformType.fromCode(type), version)
fun DependencyHandler.intellijPlatform(type: Provider<String>, version: String) = intellijPlatform(type.map { IntelliJPlatformType.fromCode(it) }, version)
fun DependencyHandler.intellijPlatform(type: String, version: Provider<String>) = intellijPlatform(IntelliJPlatformType.fromCode(type), version)
fun DependencyHandler.intellijPlatform(type: Provider<String>, version: Provider<String>) =
    intellijPlatform(type.map { IntelliJPlatformType.fromCode(it) }, version)

fun DependencyHandler.androidStudio(version: String) = intellijPlatform(AndroidStudio, version)
fun DependencyHandler.androidStudio(version: Provider<String>) = intellijPlatform(AndroidStudio, version)

fun DependencyHandler.clion(version: String) = intellijPlatform(CLion, version)
fun DependencyHandler.clion(version: Provider<String>) = intellijPlatform(CLion, version)

fun DependencyHandler.gateway(version: String) = intellijPlatform(Gateway, version)
fun DependencyHandler.gateway(version: Provider<String>) = intellijPlatform(Gateway, version)

fun DependencyHandler.goland(version: String) = intellijPlatform(GoLand, version)
fun DependencyHandler.goland(version: Provider<String>) = intellijPlatform(GoLand, version)

fun DependencyHandler.intellijIdeaCommunity(version: String) = intellijPlatform(IntellijIdeaCommunity, version)
fun DependencyHandler.intellijIdeaCommunity(version: Provider<String>) = intellijPlatform(IntellijIdeaCommunity, version)

fun DependencyHandler.intellijIdeaUltimate(version: String) = intellijPlatform(IntellijIdeaUltimate, version)
fun DependencyHandler.intellijIdeaUltimate(version: Provider<String>) = intellijPlatform(IntellijIdeaUltimate, version)

fun DependencyHandler.phpstorm(version: String) = intellijPlatform(PhpStorm, version)
fun DependencyHandler.phpstorm(version: Provider<String>) = intellijPlatform(PhpStorm, version)

fun DependencyHandler.pycharmProfessional(version: String) = intellijPlatform(PyCharmProfessional, version)
fun DependencyHandler.pycharmProfessional(version: Provider<String>) = intellijPlatform(PyCharmProfessional, version)

fun DependencyHandler.pycharmCommunity(version: String) = intellijPlatform(PyCharmCommunity, version)
fun DependencyHandler.pycharmCommunity(version: Provider<String>) = intellijPlatform(PyCharmCommunity, version)

fun DependencyHandler.rider(version: String) = intellijPlatform(Rider, version)
fun DependencyHandler.rider(version: Provider<String>) = intellijPlatform(Rider, version)
