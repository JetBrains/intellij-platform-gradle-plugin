import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.projectFeatures.githubIssues

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

    buildType(UnitTestsLINUX)
    buildType(UnitTestsWINDOWS)
    buildType(UnitTestsMACOS)

    features {
        githubIssues {
            id = "PROJECT_EXT_621"
            displayName = "JetBrains/gradle-intellij-plugin"
            repositoryURL = "https://github.com/JetBrains/gradle-intellij-plugin"
        }
    }
}

object UnitTestsLINUX : BuildType({
    name = "Unit Tests (Linux)"

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        gradle {
            name = "Unit Tests – Gradle 7.6"
            tasks = "check -PtestGradleVersion=7.6"
        }
        gradle {
            name = "Unit Tests – Gradle 8.6"
            tasks = "check -PtestGradleVersion=8.6"
        }
        gradle {
            name = "Unit Tests – Gradle 8.7-rc-3"
            tasks = "check -PtestGradleVersion=8.7-rc-3"
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
        equals("teamcity.agent.jvm.os.family", "Linux")
    }
})

object UnitTestsMACOS : BuildType({
    name = "Unit Tests (macOS)"
    paused = true

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        gradle {
            name = "Unit Tests – Gradle 7.6"
            tasks = "check -PtestGradleVersion=7.6"
        }
        gradle {
            name = "Unit Tests – Gradle 8.6"
            tasks = "check -PtestGradleVersion=8.6"
        }
        gradle {
            name = "Unit Tests – Gradle 8.7-rc-3"
            tasks = "check -PtestGradleVersion=8.7-rc-3"
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
        equals("teamcity.agent.jvm.os.family", "macOS")
    }
})

object UnitTestsWINDOWS : BuildType({
    name = "Unit Tests (Windows)"
    paused = true

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        gradle {
            name = "Unit Tests – Gradle 7.6"
            tasks = "check -PtestGradleVersion=7.6"
        }
        gradle {
            name = "Unit Tests – Gradle 8.6"
            tasks = "check -PtestGradleVersion=8.6"
        }
        gradle {
            name = "Unit Tests – Gradle 8.7-rc-3"
            tasks = "check -PtestGradleVersion=8.7-rc-3"
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
        equals("teamcity.agent.jvm.os.family", "Windows")
    }
})
