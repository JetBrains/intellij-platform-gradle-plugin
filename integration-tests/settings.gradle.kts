pluginManagement {
    repositories {
        mavenLocal()
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
    }

    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.jetbrains.intellij") {
                useModule("org.jetbrains.intellij.plugins:gradle-intellij-plugin:${requested.version}")
            }
        }
    }
}

rootProject.name = "integration-tests"

// Include current sources if building project locally.
// On CI, `mavenLocal` is used instead for the sake of performance.
if (!System.getenv().containsKey("CI")) {
    includeBuild("..")
}

// Avoid including all subproject as all of them will have to be preconfigured by Gradle.
// Load only the givem subproject provided with INTEGRATION_TEST environment variable.
if (!System.getenv().containsKey("INTEGRATION_TEST")) {
    throw GradleException("INTEGRATION_TEST environment variable has to be provided with the integration test subroject name specified.")
}
include(System.getenv().get("INTEGRATION_TEST"))
include(System.getenv().get("INTEGRATION_TEST") + ":submodule")
