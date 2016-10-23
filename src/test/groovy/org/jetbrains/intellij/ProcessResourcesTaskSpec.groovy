package org.jetbrains.intellij

import org.gradle.api.plugins.JavaPlugin
import org.gradle.tooling.model.GradleProject

class ProcessResourcesTaskSpec extends IntelliJPluginSpecBase {
    def 'use patched plugin xml files'() {
        given:
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"

        when:
        def project = run(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)

        then:
        outputPluginXml(project).text == """<idea-plugin version="2">
  <idea-version since-build="141.1010" until-build="141.*"/>
</idea-plugin>
"""
    }

    def 'do not break incremental processing'() {
        given:
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"

        when:
        run(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)
        run(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)

        then:
        stdout.contains(":" + JavaPlugin.PROCESS_RESOURCES_TASK_NAME + " UP-TO-DATE")
    }

    def 'update resources on updated patched xml files'() {
        given:
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"
        buildFile << "version='0.42.123'\nintellij { version = '14.1.3' }\n"

        when:
        run(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)
        buildFile << "patchPluginXml { sinceBuild 'Oh' }"
        def project = run(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)

        then:
        !stdout.contains(":" + JavaPlugin.PROCESS_RESOURCES_TASK_NAME + " UP-TO-DATE")
        outputPluginXml(project).text == """<idea-plugin version="2">
  <version>0.42.123</version>
  <idea-version since-build="Oh" until-build="141.*"/>
</idea-plugin>
"""
    }

    def outputPluginXml(GradleProject project) {
        new File(project.buildDirectory, "resources/main/META-INF/").listFiles().first()
    }
}
