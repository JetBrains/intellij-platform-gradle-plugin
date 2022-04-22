#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

with(__FILE__.toPath()) {
    runGradleTask("patchPluginXml").let { logs ->
        logs matchesRegex ":plugin-xml-patching:patchPluginXml .*? completed."
    }

    buildDirectory containsFile "patchedPluginXmlFiles/plugin.xml"

    patchedPluginXml containsText "<version>1.0.0</version>"
    patchedPluginXml containsText "<idea-version since-build=\"211\" until-build=\"213.*\" />"
}
