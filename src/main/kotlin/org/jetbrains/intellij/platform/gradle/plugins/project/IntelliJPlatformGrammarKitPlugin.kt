// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.plugins.checkGradleVersion
import org.jetbrains.intellij.platform.gradle.tasks.GenerateLexerTask
import org.jetbrains.intellij.platform.gradle.tasks.GenerateParserTask
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.create
import org.jetbrains.intellij.platform.gradle.utils.dependenciesHelper

abstract class IntelliJPlatformGrammarKitPlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: ${Plugins.GRAMMARKIT}")

        checkGradleVersion()

        with(project.plugins) {
            apply(IntelliJPlatformBasePlugin::class)
        }

        val dependenciesHelper by lazy {
            project.dependenciesHelper
        }

        with(project.configurations) configurations@{
            create(
                name = Configurations.INTELLIJ_PLATFORM_GRAMMAR_KIT,
                description = "GrammarKit dependency configuration",
            ) {
                defaultDependencies {
                    add(dependenciesHelper.createGrammarKit())
                }
            }

            create(
                name = Configurations.INTELLIJ_PLATFORM_JFLEX,
                description = "JFlex dependency configuration",
            ) {
                defaultDependencies {
                    add(dependenciesHelper.createJFlex())
                }
            }
        }

        listOf(
            GenerateLexerTask,
            GenerateParserTask,
        ).forEach {
            it.register(project)
        }
    }
}
