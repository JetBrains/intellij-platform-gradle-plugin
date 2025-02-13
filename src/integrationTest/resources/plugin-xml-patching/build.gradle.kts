val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type")
val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version")
val languageVersionProperty = providers.gradleProperty("languageVersion").map { it.toInt() }
val sinceBuildProperty = providers.gradleProperty("sinceBuild")
val untilBuildProperty = providers.gradleProperty("untilBuild")

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

version = "1.0.0"

kotlin {
    jvmToolchain(languageVersionProperty.get())
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(intellijPlatformTypeProperty, intellijPlatformVersionProperty)
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = sinceBuildProperty
            untilBuild = untilBuildProperty
        }
    }
}
