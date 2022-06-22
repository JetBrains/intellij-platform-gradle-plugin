#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

__FILE__.init {
    runGradleTask("assemble").let { logs ->
        logs matchesRegex "Skipping task ':jar-manifest-file:assemble' as it has no actions."
    }

    pluginJar containsFileInArchive "META-INF/MANIFEST.MF"
    with(pluginJar readEntry "META-INF/MANIFEST.MF") {
        this containsText "Version: 1.0.0"
        this containsText "Build-Plugin: Gradle IntelliJ Plugin"
        this containsText "Build-Plugin-Version:"
    }
}
