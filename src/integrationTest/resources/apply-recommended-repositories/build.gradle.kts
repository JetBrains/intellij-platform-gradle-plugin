val sinceBuildProperty = providers.gradleProperty("sinceBuild")
val languageVersionProperty = providers.gradleProperty("languageVersion").map { JavaLanguageVersion.of(it) }
val downloadDirectoryProperty = providers.gradleProperty("downloadDirectory").map { file(it) }

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(providers.gradleProperty("intellijPlatform.type"), providers.gradleProperty("intellijPlatform.version"))
        instrumentationTools()
    }
}

afterEvaluate {
    repositories
        .filterIsInstance<UrlArtifactRepository>()
        .mapNotNull { it.url }
        .joinToString(";", prefix = "repositories = ")
        .let { println(it) }
}
