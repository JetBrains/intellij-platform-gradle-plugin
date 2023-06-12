val intellijVersionProperty = project.property("intellijVersion").toString()
val sinceBuildProperty = project.property("sinceBuild").toString()
val languageVersionProperty = project.property("languageVersion").toString()
val downloadDirProperty = project.property("downloadDir").toString()

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(languageVersionProperty))
    }
}

intellij {
    version.set(intellijVersionProperty)
}

tasks {
    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        sinceBuild.set(sinceBuildProperty)
    }

    runPluginVerifier {
        downloadDir.set(downloadDirProperty)
    }
}
