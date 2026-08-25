import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.matrix
import jetbrains.buildServer.configs.kotlin.projectFeatures.githubIssues
import jetbrains.buildServer.configs.kotlin.triggers.vcs

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

version = "2026.1"

project {
    description = "Gradle plugin for building plugins for IntelliJ-based IDEs – https://github.com/JetBrains/intellij-platform-gradle-plugin"

    buildType(UnitTests)

    features {
        githubIssues {
            id = "PROJECT_EXT_621"
            displayName = "JetBrains/intellij-platform-gradle-plugin"
            repositoryURL = "https://github.com/JetBrains/intellij-platform-gradle-plugin"
        }
    }
}

object UnitTests : BuildType({
    name = "Unit Tests"

    vcs {
        root(DslContext.settingsRoot)
    }

    triggers {
        vcs {
            triggerRules = """
                -:/.github/**
                -:/.teamcity/**
            """.trimIndent()
        }
    }

    steps {
        gradle {
            name = "Run Tests"
            tasks = "test -PtestGradleVersion=%testGradleVersion% -PtestGradleUserHome=\"%teamcity.build.checkoutDir%/.gradle/testGradleHome\" -PtestMaxParallelForks=1 --console=plain --no-build-cache"
            conditions {
                doesNotEqual("os", "Windows")
            }
        }
        gradle {
            name = "Run Tests (Windows)"
            tasks = "test -PtestGradleVersion=%testGradleVersion% -PtestGradleUserHome=\"%teamcity.build.checkoutDir%/.gradle/testGradleHome\" -PtestMaxParallelForks=2 --console=plain --no-build-cache"
            conditions {
                equals("os", "Windows")
            }
        }
    }

    features {
        matrix {
            os = listOf(
                value("Windows"),
                value("Linux"),
                value("Mac OS"),
            )
            param(
                "testGradleVersion",
                listOf(
                    value("9.0.0"),
                    value("9.7.1"),
                    value("nightly"),
                ),
            )
        }
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
})
