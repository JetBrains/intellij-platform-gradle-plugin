package org.jetbrains.intellij

import org.gradle.api.plugins.BasePlugin
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DownloadIntelliJPluginsSpec : IntelliJPluginSpecBase() {

    private val mavenCacheDir = File(gradleHome, "caches/modules-2/files-2.1/")
    private val pluginsCacheDir = File(gradleHome, "caches/modules-2/files-2.1/com.jetbrains.intellij.idea/")

    @BeforeTest
    override fun setUp() {
        super.setUp()

        mavenCacheDir.delete()
        pluginsCacheDir.delete()
    }

    @Test
    fun `download zip plugin from non-default channel`() {
        buildFile.groovy("""
            intellij {
                plugins = ["CSS-X-Fire:1.55@nightly"]
            }
        """)

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        val pluginDir = File(mavenCacheDir, "nightly.com.jetbrains.plugins/CSS-X-Fire/1.55")
        pluginDir.list()?.let {
            assertTrue(it.contains("b36713d18b7845349268d9ba4ce4fedaff50d7a5"))
        }

        File(pluginDir, "b36713d18b7845349268d9ba4ce4fedaff50d7a5").list()?.let {
            assertTrue(it.contains("CSS-X-Fire-1.55.zip"))
        }
        File(pluginsCacheDir, "unzipped.nightly.com.jetbrains.plugins").list()?.let {
            assertTrue(it.contains("CSS-X-Fire-1.55"))
        }
    }

    @Test
    fun `download zip plugin`() {
        buildFile.groovy("""
            intellij {
                plugins = ["org.intellij.plugins.markdown:201.6668.74"]
            }
        """)

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        val pluginDir = File(mavenCacheDir, "com.jetbrains.plugins/org.intellij.plugins.markdown/201.6668.74")
        pluginDir.list()?.let {
            assertTrue(it.contains("5c9f1865c461d37e60c7083e8db9ad40c4bed98c"))
        }

        File(pluginDir, "5c9f1865c461d37e60c7083e8db9ad40c4bed98c").list()?.let {
            assertTrue(it.contains("org.intellij.plugins.markdown-201.6668.74.zip"))
        }
        File(pluginsCacheDir, "unzipped.com.jetbrains.plugins").list()?.let {
            assertTrue(it.contains("org.intellij.plugins.markdown-201.6668.74"))
        }
    }

    @Test
    fun `download jar plugin`() {
        buildFile.groovy("""
            intellij {
                plugins = ["org.jetbrains.postfixCompletion:0.8-beta"]
            }
        """)

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        val pluginDir = File(mavenCacheDir, "com.jetbrains.plugins/org.jetbrains.postfixCompletion/0.8-beta")
        pluginDir.list()?.let {
            assertTrue(it.contains("4c0b55a3dea095dd8e085b1c65fcc7b917b74dd5"))
        }
        File(pluginDir, "4c0b55a3dea095dd8e085b1c65fcc7b917b74dd5").list()?.let {
            assertTrue(it.contains("org.jetbrains.postfixCompletion-0.8-beta.jar"))
        }
    }

    @Test
    fun `download plugin from custom repository`() {
        val resource = javaClass.classLoader.getResource("custom-repo/updatePlugins.xml")

        buildFile.groovy("""
            intellij {
                pluginsRepo {
                    custom('${resource}')
                }
                plugins = ["com.intellij.plugins.emacskeymap:201.6251.22"]
            }
        """)

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        File(pluginsCacheDir, "unzipped.com.jetbrains.plugins").list()?.let {
            assertTrue(it.contains("com.intellij.plugins.emacskeymap-201.6251.22"))
        }
    }

    @Test
    fun `download plugin from custom repository 2`() {
        val resource = javaClass.classLoader.getResource("custom-repo/plugins.xml")

        buildFile.groovy("""
            intellij {
                pluginsRepo {
                    custom('${resource}')
                }
                
                plugins = ["com.intellij.plugins.emacskeymap:201.6251.22"]
            } 
        """)

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        File(pluginsCacheDir, "unzipped.com.jetbrains.plugins").list()?.let {
            assertTrue(it.contains("com.intellij.plugins.emacskeymap-201.6251.22"))
        }
    }
}
