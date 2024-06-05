// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.project
import jetbrains.buildServer.configs.kotlin.projectFeatures.githubIssues
import jetbrains.buildServer.configs.kotlin.sequential
import jetbrains.buildServer.configs.kotlin.ui.add
import jetbrains.buildServer.configs.kotlin.version

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2024.03"

project {
    description = "Gradle plugin for building plugins for IntelliJ-based IDEs – https://github.com/JetBrains/intellij-platform-gradle-plugin"

    features {
        githubIssues {
            id = "IJPGP"
            displayName = "JetBrains/intellij-platform-gradle-plugin"
            repositoryURL = "https://github.com/JetBrains/intellij-platform-gradle-plugin"
        }
    }

    val operatingSystems = listOf("Linux", "Windows", "macOS")
    val gradleVersions = listOf("8.2", "8.8")

    val buildChain = sequential {
        operatingSystems.forEach { os ->
            this@project.buildType {
                id("UnitTests${os.uppercase()}")
                name = "Unit Tests ($os)"

                vcs {
                    root(DslContext.settingsRoot)
                }

                steps {
                    gradleVersions.forEach { gradleVersion ->
                        gradle {
                            name = "Unit Tests – Gradle $gradleVersion"
                            tasks = "check -PtestGradleVersion=$gradleVersion"
                        }
                    }
                }

                features {
                    commitStatusPublisher {
                        publisher = github {
                            githubUrl = "https://api.github.com"
                            authType = personalToken {
                                token = "credentialsJSON:7b4ae65b-efad-4ea8-8ddf-b48502524605"
                            }
                        }
                        param("github_oauth_user", "hsz")
                    }
                }

                requirements {
                    add {
                        equals("teamcity.agent.jvm.os.family", os)
                    }
                }
            }
        }
    }

    buildChain.buildTypes().forEach { buildType(it) }
}
