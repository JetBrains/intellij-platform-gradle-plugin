package org.jetbrains.intellij

import org.gradle.testkit.runner.TaskOutcome

class PatchPluginXmlSpec extends IntelliJPluginSpecBase {
    def 'patch version and since until builds'() {
        given:
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"
        buildFile << "version='0.42.123'\nintellij { version = '14.1.4' }"

        when:
        def output = build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME).output

        then:
        patchedPluginXml.text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <idea-version since-build="141.1532" until-build="141.*"/>
</idea-plugin>
"""
        assert !output.contains('will be overwritten')
    }

    def 'patch description'() {
        given:
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"
        buildFile << "version='0.42.123'\nintellij { version = '14.1.4' }\n"
        buildFile << "patchPluginXml { pluginDescription = 'Plugin pluginDescription' }"

        when:
        def output = build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME).output

        then:
        patchedPluginXml.text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <description>Plugin pluginDescription</description>
  <idea-version since-build="141.1532" until-build="141.*"/>
</idea-plugin>
"""
        assert !output.contains('will be overwritten')
    }

    def 'patch patching preserves UTF-8 characters'() {
        given:
        pluginXml.write("<idea-plugin version=\"2\" someattr=\"\u2202\"></idea-plugin>", "UTF-8")
        buildFile << "version='0.42.123'\nintellij { version = '14.1.4' }"

        when:
        def output = build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME).output

        then:
        patchedPluginXml.getText("UTF-8") == """<idea-plugin version="2" someattr="\u2202">
  <version>0.42.123</version>
  <idea-version since-build="141.1532" until-build="141.*"/>
</idea-plugin>
"""
        assert !output.contains('will be overwritten')
    }

    def 'patch change notes'() {
        given:
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"
        buildFile << "version='0.42.123'\nintellij { version = '14.1.4' }\n"
        buildFile << "patchPluginXml { changeNotes = 'change notes' }"

        when:
        def output = build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME).output

        then:
        patchedPluginXml.text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <change-notes>change notes</change-notes>
  <idea-version since-build="141.1532" until-build="141.*"/>
</idea-plugin>
"""
        assert !output.contains('will be overwritten')
    }

    def 'patch id'() {
        given:
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"
        buildFile << "version='0.42.123'\nintellij { version = '14.1.4' }\n"
        buildFile << "patchPluginXml { pluginId = 'my.plugin.id' }"

        when:
        def output = build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME).output

        then:
        patchedPluginXml.text == """<idea-plugin version="2">
  <id>my.plugin.id</id>
  <version>0.42.123</version>
  <idea-version since-build="141.1532" until-build="141.*"/>
</idea-plugin>
"""
        assert !output.contains('will be overwritten')
    }

    def 'do not update id if pluginId is undefined'() {
        given:
        pluginXml << "<idea-plugin version=\"2\"><id>my.plugin.id</id></idea-plugin>"
        buildFile << "version='0.42.123'\nintellij { version = '14.1.4' }\n"

        when:
        def output = build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME).output

        then:
        patchedPluginXml.text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <idea-version since-build="141.1532" until-build="141.*"/>
  <id>my.plugin.id</id>
</idea-plugin>
"""
        assert !output.contains('will be overwritten')
    }

    def 'same since and until builds'() {
        given:
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"
        buildFile << "version='0.42.123'\nintellij { version = '14.1.4'; sameSinceUntilBuild = true }"
        when:
        def output = build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME).output
        then:
        patchedPluginXml.text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <idea-version since-build="141.1532" until-build="141.1532.*"/>
</idea-plugin>
"""
        assert !output.contains('will be overwritten')
    }

    def 'add version tags in the beginning of file'() {
        given:
        pluginXml << "<idea-plugin version=\"2\">\n<id>org.jetbrains.erlang</id>\n</idea-plugin>"
        buildFile << "version='0.42.123'\nintellij { version = '14.1.4' }"
        when:
        def output = build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME).output
        then:
        patchedPluginXml.text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <idea-version since-build="141.1532" until-build="141.*"/>
  <id>org.jetbrains.erlang</id>
</idea-plugin>
"""
        assert !output.contains('will be overwritten')
    }

    def 'override version and since until builds'() {
        given:
        pluginXml << """<idea-plugin version="2">
<version>my_version</version>
<idea-version since-build='1' until-build='2'>my_version</idea-version>
</idea-plugin>"""
        buildFile << "version='0.42.123'\nintellij { version = '14.1.4' }"
        when:
        def output = build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME).output
        then:
        patchedPluginXml.text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <idea-version since-build="141.1532" until-build="141.*">my_version</idea-version>
</idea-plugin>
"""
        assert output.contains('attribute `since-build=[1]` of `idea-version` tag will be set to `141.1532`')
        assert output.contains('attribute `until-build=[2]` of `idea-version` tag will be set to `141.*`')
        assert output.contains('value of `version[my_version]` tag will be set to `0.42.123`')
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
        def output = build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME).output

        then:
        patchedPluginXml.text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <idea-version since-build="1" until-build="2">my_version</idea-version>
</idea-plugin>
"""
        assert !output.contains('will be overwritten')
    }

    def 'do not update version tag if project.version is undefined'() {
        given:
        pluginXml << "<idea-plugin version=\"2\">\n  <version>0.10.0</version>\n</idea-plugin>"
        buildFile << "intellij { version = '14.1.4' }"

        when:
        def output = build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME).output

        then:
        patchedPluginXml.text == """<idea-plugin version="2">
  <idea-version since-build="141.1532" until-build="141.*"/>
  <version>0.10.0</version>
</idea-plugin>
"""
        assert !output.contains('will be overwritten')
    }

    def 'skip patch task if intellij version did not changed'() {
        given:
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"
        buildFile << "version='0.42.123'\nintellij { version = '14.1.4' }"

        when:
        build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME)
        def result = build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME)

        then:
        result.task(":$IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME").outcome == TaskOutcome.UP_TO_DATE
        patchedPluginXml.text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <idea-version since-build="141.1532" until-build="141.*"/>
</idea-plugin>
"""
    }

    def 'patch version and since until builds on intellij version changing'() {
        given:
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"
        buildFile << "version='0.42.123'\nintellij { version = '2017.2.5' }"

        when:
        build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME)
        buildFile << "\nintellij { version = '14.1.4' }"
        def result = build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME)

        then:
        result.task(":$IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME").outcome != TaskOutcome.UP_TO_DATE
        patchedPluginXml.text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <idea-version since-build="141.1532" until-build="141.*"/>
</idea-plugin>
"""
    }

    def 'patch plugin xml with doctype'() {
        given:
        buildFile << "version='0.42.123'\nintellij { version = '14.1.4' }"
        pluginXml << """<!DOCTYPE idea-plugin PUBLIC \"Plugin/DTD\" \"http://plugins.jetbrains.com/plugin.dtd\">
<idea-plugin version=\"2\"></idea-plugin>
"""

        when:
        def output = build(IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME).output

        then:
        patchedPluginXml.text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <idea-version since-build="141.1532" until-build="141.*"/>
</idea-plugin>
"""
        assert !output.contains('will be overwritten')
    }

    private File getPatchedPluginXml() {
        new File(buildDirectory, IntelliJPlugin.PLUGIN_XML_DIR_NAME).listFiles().first()
    }
}