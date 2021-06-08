package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DownloadRobotServerPluginTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `download old robot server plugin task`() {
        writeJavaFile()

        file("src/main/resources/META-INF/other.xml").xml("""
            <idea-plugin />
        """)

        file("src/main/resources/META-INF/nonIncluded.xml").xml("""
            <idea-plugin />
        """)

        pluginXml.xml("""
            <idea-plugin>
                <depends config-file="other.xml"/>
            </idea-plugin>
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                pluginName = 'myPluginName'
                plugins = ['copyright']
            }
            downloadRobotServerPlugin.version = '0.10.0'
            dependencies {
                implementation 'joda-time:joda-time:2.8.1'
            }
        """)

        build(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)

        assertTrue(
            collectPaths(File(buildDirectory, "robotServerPlugin"))
                .containsAll(setOf("/robot-server-plugin/lib/robot-server-plugin-0.10.0.jar"))
        )
    }

    @Test
    fun `download new robot server plugin task`() {
        writeJavaFile()

        file("src/main/resources/META-INF/other.xml").xml("""
            <idea-plugin />
        """)

        file("src/main/resources/META-INF/nonIncluded.xml").xml("""
            <idea-plugin />
        """)

        pluginXml.xml("""
            <idea-plugin>
                <depends config-file="other.xml"/>
            </idea-plugin>
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                pluginName = 'myPluginName'
                plugins = ['copyright']
            }
            downloadRobotServerPlugin.version = '0.11.1'
            dependencies {
                implementation 'joda-time:joda-time:2.8.1'
            }
        """)

        build(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)

        assertTrue(
            collectPaths(File(buildDirectory, "robotServerPlugin"))
                .containsAll(setOf("/robot-server-plugin/lib/robot-server-plugin-0.11.1.jar"))
        )
    }

    @Test
    fun `download latest robot server plugin task`() {
        writeJavaFile()

        file("src/main/resources/META-INF/other.xml").xml("""
            <idea-plugin />
        """)

        file("src/main/resources/META-INF/nonIncluded.xml").xml("""
            <idea-plugin />
        """)

        pluginXml.xml("""
            <idea-plugin>
                <depends config-file="other.xml"/>
            </idea-plugin>
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                pluginName = 'myPluginName'
                plugins = ['copyright']
            }
            dependencies {
                implementation 'joda-time:joda-time:2.8.1'
            }
        """)

        build(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)

        val resolvedVersion = DownloadRobotServerPluginTask.resolveLatestVersion()
        assertTrue(
            collectPaths(File(buildDirectory, "robotServerPlugin"))
                .containsAll(setOf("/robot-server-plugin/lib/robot-server-plugin-$resolvedVersion.jar"))
        )
    }

    @Test
    fun `reuse configuration cache`() {
        build(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME, "--configuration-cache")
        val result = build(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME, "--configuration-cache")

        assertTrue(result.output.contains("Reusing configuration cache."))
    }
}
