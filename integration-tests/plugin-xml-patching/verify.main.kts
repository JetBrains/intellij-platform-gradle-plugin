#!/usr/bin/env kotlin -J-ea

@file:Import("../verify.utils.kts")

buildOutput containsText "BUILD SUCCESSFUL"
buildOutput matchesRegex ":plugin-xml-patching:patchPluginXml .*? completed."

buildDirectory containsFile "patchedPluginXmlFiles/plugin.xml"

patchedPluginXml containsText "<version>1.0.0</version>"
patchedPluginXml containsText "<idea-version since-build=\"2021.1\" until-build=\"2021.3.*\" />"
