package org.jetbrains.intellij.tasks

import org.gradle.api.plugins.BasePlugin
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.IntelliJPluginSpecBase
import org.junit.Assume.assumeFalse
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DownloadIntelliJPluginsSpec : IntelliJPluginSpecBase() {

    private val pluginsRepositoryCacheDir = File(gradleHome, "caches/modules-2/files-2.1/com.jetbrains.plugins")
    private val pluginsNightlyRepositoryCacheDir = File(gradleHome, "caches/modules-2/files-2.1/nightly.com.jetbrains.plugins")
    private val pluginsCacheDir = File(gradleHome, "caches/modules-2/files-2.1/com.jetbrains.intellij.idea/unzipped.com.jetbrains.plugins")
    private val pluginsNightlyCacheDir =
        File(gradleHome, "caches/modules-2/files-2.1/com.jetbrains.intellij.idea/unzipped.nightly.com.jetbrains.plugins")

    @BeforeTest
    override fun setUp() {
        super.setUp()

        pluginsRepositoryCacheDir.delete()
        pluginsNightlyRepositoryCacheDir.delete()
        pluginsCacheDir.delete()
        pluginsNightlyCacheDir.delete()
    }


    @Test
    fun `download plugin through maven block`() {
        buildFile.groovy("""
            intellij {
                plugins = ["com.intellij.lang.jsgraphql:2.9.1"]
                pluginsRepositories {
                      maven {
                        url = uri("$pluginsRepository")
                      }
                } 
            }
        """)

        build(BasePlugin.ASSEMBLE_TASK_NAME)
        val pluginDir = File(pluginsRepositoryCacheDir, "com.intellij.lang.jsgraphql/2.9.1")
        pluginDir.list()?.let {
            assertTrue(it.contains("7145468b33e1c1e246ec5c9127c219d1c7e54cc2"))
        }

        File(pluginDir, "7c426d70e7d2b270ba3eb896fc7c5e7cbf8330b1").list()?.let {
            assertTrue(it.contains("com.intellij.lang.jsgraphql-2.9.1.zip"))
        }

        File(pluginDir, "7145468b33e1c1e246ec5c9127c219d1c7e54cc2").list()?.let {
            assertTrue(it.contains("com.intellij.lang.jsgraphql-2.9.1.pom"))
        }
    }

    @Test
    fun `download zip plugin from non-default channel`() {
        buildFile.groovy("""
            intellij {
                plugins = ["CSS-X-Fire:1.55@nightly"]
            }
        """)

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        val pluginDir = File(pluginsNightlyRepositoryCacheDir, "nightly.com.jetbrains.plugins/CSS-X-Fire/1.55")
        pluginDir.list()?.let {
            assertTrue(it.contains("9cab70cc371b245cd808ade65630f505a6443b0d"))
        }

        File(pluginDir, "9cab70cc371b245cd808ade65630f505a6443b0d").list()?.let {
            assertTrue(it.contains("CSS-X-Fire-1.55.zip"))
        }
        pluginsNightlyCacheDir.list()?.let {
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

        val pluginDir = File(pluginsRepositoryCacheDir, "com.jetbrains.plugins/org.intellij.plugins.markdown/201.6668.74")
        pluginDir.list()?.let {
            assertTrue(it.contains("17328855fcd031f39a805db934c121eaa25dedfb"))
        }

        File(pluginDir, "17328855fcd031f39a805db934c121eaa25dedfb").list()?.let {
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

        val pluginDir = File(pluginsRepositoryCacheDir, "com.jetbrains.plugins/org.jetbrains.postfixCompletion/0.8-beta")
        pluginDir.list()?.let {
            assertTrue(it.contains("dd37fa3fb1ecbf3d1c2fdb0049ba821fd32f2565"))
        }
        File(pluginDir, "dd37fa3fb1ecbf3d1c2fdb0049ba821fd32f2565").list()?.let {
            assertTrue(it.contains("org.jetbrains.postfixCompletion-0.8-beta.jar"))
        }
    }

    @Test
    fun `download plugin from custom repository`() {
        val resource = javaClass.classLoader.getResource("custom-repo/updatePlugins.xml")

        buildFile.groovy("""
            intellij {
                pluginsRepositories {
                    custom('${resource}')
                }
                plugins = ["com.intellij.plugins.emacskeymap:201.6251.22"]
            }
        """)

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        pluginsCacheDir.list()?.let {
            assertTrue(it.contains("com.intellij.plugins.emacskeymap-201.6251.22"))
        }
    }

    @Test
    fun `download plugin from custom repository 2`() {
        val resource = javaClass.classLoader.getResource("custom-repo-2/plugins.xml")

        buildFile.groovy("""
            intellij {
                pluginsRepositories {
                    custom('${resource}')
                }
                plugins = ["com.intellij.plugins.emacskeymap:201.6251.22"]
            } 
        """)

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        pluginsCacheDir.list()?.let {
            assertTrue(it.contains("com.intellij.plugins.emacskeymap-201.6251.22"))
        }
    }

    @Test
    fun `download plugin from custom repository with query`() {
        assumeFalse(OperatingSystem.current().isWindows)
        val resource = javaClass.classLoader.getResource("custom-repo-2/plugins.xml")

        buildFile.groovy("""
            intellij {
                pluginsRepositories {
                    custom('${resource}?query=1')
                }
                plugins = ["com.intellij.plugins.emacskeymap:201.6251.22"]
            }
        """)

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        pluginsCacheDir.list()?.let {
            assertTrue(it.contains("com.intellij.plugins.emacskeymap-201.6251.22"))
        }
    }

    @Test
    fun `download plugin from custom repository without xml`() {
        val resource = javaClass.classLoader.getResource("custom-repo")

        buildFile.groovy("""
            intellij {
                pluginsRepositories {
                    custom('${resource}')
                }
                plugins = ["com.intellij.plugins.emacskeymap:201.6251.22"]
            }
        """)

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        pluginsCacheDir.list()?.let {
            assertTrue(it.contains("com.intellij.plugins.emacskeymap-201.6251.22"))
        }
    }

    @Test
    fun `download plugin from custom repository without xml with query`() {
        val resource = javaClass.classLoader.getResource("custom-repo")

        buildFile.groovy("""
            intellij {
                pluginsRepositories {
                    custom('${resource}?query=1')
                }
                plugins = ["com.intellij.plugins.emacskeymap:201.6251.22"]
            }
        """)

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        pluginsCacheDir.list()?.let {
            assertTrue(it.contains("com.intellij.plugins.emacskeymap-201.6251.22"))
        }
    }
}
