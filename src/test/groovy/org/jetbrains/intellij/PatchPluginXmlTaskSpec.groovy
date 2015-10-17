package org.jetbrains.intellij

import org.gradle.tooling.model.GradleProject

class PatchPluginXmlTaskSpec extends IntelliJPluginSpecBase {
    def 'patch version and since until builds'() {
        given:
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"
        buildFile << "version='0.42.123'\nintellij { version = '14.1.4' }"
        when:
        def project = run(PatchPluginXmlTask.NAME)
        then:
        ouputPluginXml(project).text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <idea-version since-build="141.1532.4" until-build="141.9999"/>
</idea-plugin>
"""
    }

    def 'add version tags in the beginning of file'() {
        given:
        pluginXml << "<idea-plugin version=\"2\">\n<id>org.jetbrains.erlang</id>\n</idea-plugin>"
        buildFile << "version='0.42.123'\nintellij { version = '14.1.4' }"
        when:
        def project = run(PatchPluginXmlTask.NAME)
        then:
        println(ouputPluginXml(project).text)
        ouputPluginXml(project).text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <idea-version since-build="141.1532.4" until-build="141.9999"/>
  <id>org.jetbrains.erlang</id>
</idea-plugin>
"""
    }

    def 'override version and since until builds'() {
        given:
        pluginXml << """<idea-plugin version="2">
<version>my_version</version>
<idea-version since-build='1' until-build='2'>my_version</idea-version>
</idea-plugin>"""
        buildFile << "version='0.42.123'\nintellij { version = '14.1.4' }"
        when:
        def project = run(PatchPluginXmlTask.NAME)
        then:
        ouputPluginXml(project).text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <idea-version since-build="141.1532.4" until-build="141.9999">my_version</idea-version>
</idea-plugin>
"""
    }

    def 'take extension setting into account while patching'() {
        given:
        pluginXml << """<idea-plugin version="2">
<version>my_version</version>
<idea-version since-build='1' until-build='2'>my_version</idea-version>
</idea-plugin>"""
        buildFile << """
version='0.42.123'
intellij { 
    version = '14.1.4'
    updateSinceUntilBuild = false 
}"""
        when:
        def project = run(PatchPluginXmlTask.NAME)
        then:
        ouputPluginXml(project).text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <idea-version since-build="1" until-build="2">my_version</idea-version>
</idea-plugin>
"""
    }

    def 'do not update version tag if project.version is undefined'() {
        given:
        pluginXml << "<idea-plugin version=\"2\">\n  <version>0.10.0</version>\n</idea-plugin>"
        buildFile << "intellij { version = '14.1.4' }"
        when:
        def project = run(PatchPluginXmlTask.NAME)
        then:
        ouputPluginXml(project).text == """<idea-plugin version="2">
  <idea-version since-build="141.1532.4" until-build="141.9999"/>
  <version>0.10.0</version>
</idea-plugin>
"""
    }

    private static File ouputPluginXml(GradleProject project) {
        new File(project.buildDirectory, "resources/main/META-INF/plugin.xml")
    }
}
