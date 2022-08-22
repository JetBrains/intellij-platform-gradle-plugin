val intellijVersionProperty = project.property("intellijVersion").toString()
val sinceBuildProperty = project.property("sinceBuild").toString()
val languageVersionProperty = project.property("languageVersion").toString()

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(languageVersionProperty))
    }
}

intellij {
    version.set(intellijVersionProperty)
}

tasks {
    patchPluginXml {
        sinceBuild.set(sinceBuildProperty)
    }
}
