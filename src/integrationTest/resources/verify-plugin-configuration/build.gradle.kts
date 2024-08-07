val intellijVersionProperty = providers.gradleProperty("intellijVersion")
val sinceBuildProperty = providers.gradleProperty("sinceBuild")
val languageVersionProperty = providers.gradleProperty("languageVersion").map { JavaLanguageVersion.of(it) }

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(intellijVersionProperty)
    }
}

kotlin {
    jvmToolchain {
        languageVersion = languageVersionProperty.get()
    }
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = sinceBuildProperty
        }
    }
}
