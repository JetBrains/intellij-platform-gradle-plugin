package org.jetbrains.intellij

import org.gradle.api.plugins.JavaPlugin
import org.gradle.testkit.runner.TaskOutcome

class ProcessResourcesTaskSpec extends IntelliJPluginSpecBase {
    def 'use patched plugin xml files'() {
        given:
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"

        when:
        build(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)

        then:
        outputPluginXml.text == '''
            <idea-plugin version="2">
              <idea-version since-build="201.6668" until-build="201.*"/>
            </idea-plugin>
            '''[1..-1].stripIndent()
    }

    def 'do not break incremental processing'() {
        given:
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"

        when:
        build(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)
        def result = build(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)

        then:
        result.task(":$JavaPlugin.PROCESS_RESOURCES_TASK_NAME").outcome == TaskOutcome.UP_TO_DATE
    }

    def 'update resources on updated patched xml files'() {
        given:
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"
        buildFile << "version='0.42.123'\n"

        when:
        build(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)
        buildFile << "patchPluginXml { sinceBuild 'Oh' }"
        def result = build(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)

        then:
        result.task(":$JavaPlugin.PROCESS_RESOURCES_TASK_NAME").outcome != TaskOutcome.UP_TO_DATE
        outputPluginXml.text == '''
            <idea-plugin version="2">
              <version>0.42.123</version>
              <idea-version since-build="Oh" until-build="201.*"/>
            </idea-plugin>
            '''[1..-1].stripIndent()
    }

    private File getOutputPluginXml() {
        new File(buildDirectory, "resources/main/META-INF/").listFiles().first()
    }
}
