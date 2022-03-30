package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.deleteLogged
import com.jetbrains.plugin.structure.base.utils.listFiles
import com.jetbrains.plugin.structure.base.utils.simpleName
import org.gradle.api.plugins.BasePlugin
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.IntelliJPluginSpecBase
import org.junit.Assume.assumeFalse
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DownloadIntelliJPluginsSpec : IntelliJPluginSpecBase() {

    private val pluginsRepositoryCacheDir = gradleHome.resolve("caches/modules-2/files-2.1/com.jetbrains.plugins")
    private val pluginsNightlyRepositoryCacheDir = gradleHome.resolve("caches/modules-2/files-2.1/nightly.com.jetbrains.plugins")
    private val pluginsCacheDir =
        gradleHome.resolve("caches/modules-2/files-2.1/com.jetbrains.intellij.idea/unzipped.com.jetbrains.plugins")
    private val pluginsNightlyCacheDir =
        gradleHome.resolve("caches/modules-2/files-2.1/com.jetbrains.intellij.idea/unzipped.nightly.com.jetbrains.plugins")

    @BeforeTest
    override fun setUp() {
        super.setUp()

        pluginsRepositoryCacheDir.deleteLogged()
        pluginsNightlyRepositoryCacheDir.deleteLogged()
        pluginsCacheDir.deleteLogged()
        pluginsNightlyCacheDir.deleteLogged()
    }

    @Test
    fun `download plugin through maven block`() {
        buildFile.groovy(
            """
            intellij {
                plugins = ["com.intellij.lang.jsgraphql:2.9.1"]
                pluginsRepositories {
                      maven {
                        url = uri("$pluginsRepository")
                      }
                } 
            }
        """
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)
        val pluginDir = pluginsRepositoryCacheDir.resolve("com.intellij.lang.jsgraphql/2.9.1")

        assertTrue {
            pluginDir.resolve("7c426d70e7d2b270ba3eb896fc7c5e7cbf8330b1").listFiles().any {
                it.simpleName == "com.intellij.lang.jsgraphql-2.9.1.zip"
            }
        }
    }

    @Test
    fun `download zip plugin from non-default channel`() {
        buildFile.groovy(
            """
            intellij {
                plugins = ["CSS-X-Fire:1.55@nightly"]
            }
        """
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        val pluginDir = pluginsNightlyRepositoryCacheDir.resolve("CSS-X-Fire/1.55")
        assertTrue {
            pluginDir.listFiles().any {
                it.simpleName == "9cab70cc371b245cd808ade65630f505a6443b0d"
            }
        }

        assertTrue {
            pluginDir.resolve("9cab70cc371b245cd808ade65630f505a6443b0d").listFiles().any {
                it.simpleName == "CSS-X-Fire-1.55.zip"
            }
        }

        assertTrue {
            pluginsNightlyCacheDir.listFiles().any {
                it.simpleName == "CSS-X-Fire-1.55"
            }
        }
    }

    @Test
    fun `download zip plugin`() {
        buildFile.groovy(
            """
            intellij {
                plugins = ["org.intellij.plugins.markdown:201.6668.74"]
            }
        """
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        val pluginDir = pluginsRepositoryCacheDir.resolve("org.intellij.plugins.markdown/201.6668.74")

        assertTrue {
            pluginDir.listFiles().any {
                it.simpleName == "17328855fcd031f39a805db934c121eaa25dedfb"
            }
        }

        assertTrue {
            pluginDir.resolve("17328855fcd031f39a805db934c121eaa25dedfb").listFiles().any {
                it.simpleName == "org.intellij.plugins.markdown-201.6668.74.zip"
            }
        }

        assertTrue {
            pluginsCacheDir.listFiles().any {
                it.simpleName == "org.intellij.plugins.markdown-201.6668.74"
            }
        }
    }

    @Test
    fun `download jar plugin`() {
        buildFile.groovy(
            """
            intellij {
                plugins = ["org.jetbrains.postfixCompletion:0.8-beta"]
            }
        """
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        val pluginDir = pluginsRepositoryCacheDir.resolve("org.jetbrains.postfixCompletion/0.8-beta")

        assertTrue {
            pluginDir.listFiles().any {
                it.simpleName == "dd37fa3fb1ecbf3d1c2fdb0049ba821fd32f2565"
            }
        }

        assertTrue {
            pluginDir.resolve("dd37fa3fb1ecbf3d1c2fdb0049ba821fd32f2565").listFiles().any {
                it.simpleName == "org.jetbrains.postfixCompletion-0.8-beta.jar"
            }
        }
    }

    @Test
    fun `download plugin from custom repository`() {
        val resource = javaClass.classLoader.getResource("custom-repo/updatePlugins.xml")

        buildFile.groovy(
            """
            intellij {
                pluginsRepositories {
                    custom('${resource}')
                }
                plugins = ["com.intellij.plugins.emacskeymap:201.6251.22"]
            }
        """
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertTrue {
            pluginsCacheDir.listFiles().any {
                it.simpleName == "com.intellij.plugins.emacskeymap-201.6251.22"
            }
        }
    }

    @Test
    fun `download plugin from custom repository 2`() {
        val resource = javaClass.classLoader.getResource("custom-repo-2/plugins.xml")

        buildFile.groovy(
            """
            intellij {
                pluginsRepositories {
                    custom('${resource}')
                }
                plugins = ["com.intellij.plugins.emacskeymap:201.6251.22"]
            }
        """
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertTrue {
            pluginsCacheDir.listFiles().any {
                it.simpleName == "com.intellij.plugins.emacskeymap-201.6251.22"
            }
        }
    }

    @Test
    fun `download plugin from custom repository with query`() {
        assumeFalse(OperatingSystem.current().isWindows)
        val resource = javaClass.classLoader.getResource("custom-repo-2/plugins.xml")

        buildFile.groovy(
            """
            intellij {
                pluginsRepositories {
                    custom('${resource}?query=1')
                }
                plugins = ["com.intellij.plugins.emacskeymap:201.6251.22"]
            }
        """
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertTrue {
            pluginsCacheDir.listFiles().any {
                it.simpleName == "com.intellij.plugins.emacskeymap-201.6251.22"
            }
        }
    }

    @Test
    fun `download plugin from custom repository without xml`() {
        val resource = javaClass.classLoader.getResource("custom-repo")

        buildFile.groovy(
            """
            intellij {
                pluginsRepositories {
                    custom('${resource}')
                }
                plugins = ["com.intellij.plugins.emacskeymap:201.6251.22"]
            }
        """
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertTrue {
            pluginsCacheDir.listFiles().any {
                it.simpleName == "com.intellij.plugins.emacskeymap-201.6251.22"
            }
        }
    }

    @Test
    fun `download plugin from custom repository without xml with query`() {
        val resource = javaClass.classLoader.getResource("custom-repo")

        buildFile.groovy(
            """
            intellij {
                pluginsRepositories {
                    custom('${resource}?query=1')
                }
                plugins = ["com.intellij.plugins.emacskeymap:201.6251.22"]
            }
        """
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertTrue {
            pluginsCacheDir.listFiles().any {
                it.simpleName == "com.intellij.plugins.emacskeymap-201.6251.22"
            }
        }
    }
}
