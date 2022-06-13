#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

with(__FILE__.toPath()) {
    runGradleTask("buildPlugin").let {
        val cssPluginIvyFileName = "CSS-ideaIU-IU-212.5712.43-withSources-3.xml"

        pluginsCacheDirectory containsFile cssPluginIvyFileName

        val ivyFile = pluginsCacheDirectory.resolve(cssPluginIvyFileName)
        ivyFile containsText """<artifact name="lib/src/src_css-api" type="zip" ext="zip" conf="sources"/>"""
    }
}
