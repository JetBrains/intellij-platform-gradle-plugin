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

afterEvaluate {
    repositories
        .filterIsInstance<UrlArtifactRepository>()
        .mapNotNull { it.url }
        .joinToString(";", prefix = "repositories = ")
        .let { println(it) }
}
