// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JavaLanguageVersion

// Reflection is used to avoid binary incompatibility when the Kotlin Gradle Plugin version at
// runtime differs from the version this plugin was compiled against.
internal fun Project.configureKotlinJvmToolchainConventions(requestedJavaLanguageVersion: Provider<JavaLanguageVersion>) {
    @Suppress("UNCHECKED_CAST") val kotlinJvmCompileClass = runCatching {
        Thread.currentThread().contextClassLoader.loadClass("org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile") as Class<Task>
    }.getOrNull() ?: return

    tasks.withType(kotlinJvmCompileClass).configureEach {
        fun Any.invokeNoArgsMethod(name: String) =
            javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }?.invoke(this)

        runCatching {
            val jvmTargetClass = Class.forName(
                "org.jetbrains.kotlin.gradle.dsl.JvmTarget",
                false,
                javaClass.classLoader,
            )
            val fromTarget = jvmTargetClass.methods.firstOrNull { it.name == "fromTarget" && it.parameterCount == 1 }
                ?: return@runCatching
            val compilerOptions = invokeNoArgsMethod("getCompilerOptions") ?: return@runCatching
            val jvmTargetProp = compilerOptions.invokeNoArgsMethod("getJvmTarget") ?: return@runCatching
            val conventionMethod =
                jvmTargetProp.javaClass.methods.firstOrNull { it.name == "convention" && it.parameterCount == 1 && it.parameterTypes[0] == Provider::class.java }
                    ?: return@runCatching

            conventionMethod.invoke(
                jvmTargetProp,
                requestedJavaLanguageVersion.map { fromTarget.invoke(null, it.toString()) },
            )
        }
    }
}
