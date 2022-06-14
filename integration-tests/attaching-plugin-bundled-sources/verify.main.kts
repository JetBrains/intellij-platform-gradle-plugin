#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

with(__FILE__.toPath()) {
    runGradleTask("buildPlugin").let {
        val goPluginIvyFileName = "go-goland-GO-212.5457.54-withSources-3.xml"

        pluginsCacheDirectory containsFile goPluginIvyFileName

        val ivyFile = pluginsCacheDirectory.resolve(goPluginIvyFileName)
        ivyFile containsText """<artifact name="lib/src/go-openapi-src" type="jar" ext="jar" conf="sources" m:classifier="unzipped.com.jetbrains.plugins"/>"""
    }
}
