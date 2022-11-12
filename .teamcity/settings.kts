import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
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

version = "2022.04"

project {

    buildType(UnitTests)

    features {
        githubIssues {
            id = "PROJECT_EXT_621"
            displayName = "JetBrains/gradle-intellij-plugin"
            repositoryURL = "https://github.com/JetBrains/gradle-intellij-plugin"
        }
    }
}


object UnitTests : BuildType({
    val gradleVersions = listOf("6.8", "6.9.3", "7.5.1", "7.6-rc-3")

    name = "Unit Tests"

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

    triggers {
        vcs {
        }
    }

    features {
        commitStatusPublisher {
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                    token = "credentialsJSON:935e7750-4963-410f-8aab-e86748770a1f"
                }
            }
            param("github_oauth_user", "hsz")
        }
    }

    requirements {
        add {
            equals("teamcity.agent.jvm.os.name", "Linux")
        }
    }
})
