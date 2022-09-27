// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
    private val pluginsDevCacheDir = File(gradleHome, "caches/modules-2/files-2.1/com.jetbrains.intellij.idea/unzipped.dev.com.jetbrains.plugins")

    @BeforeTest
    override fun setUp() {
        super.setUp()

        pluginsRepositoryCacheDir.delete()
        pluginsNightlyRepositoryCacheDir.delete()
        pluginsCacheDir.delete()
        pluginsDevCacheDir.delete()
    }

    @Test
    fun `download plugin through maven block`() {
        buildFile.groovy("""
            intellij {
                plugins = ["com.intellij.lang.jsgraphql:3.1.3"]
                pluginsRepositories {
                      maven {
                        url = uri("$pluginsRepository")
                      }
                } 
            }
        """)

        build(BasePlugin.ASSEMBLE_TASK_NAME)
        val pluginDir = File(pluginsRepositoryCacheDir, "com.intellij.lang.jsgraphql/3.1.3")

        pluginDir.list()?.let {
            assertTrue(it.contains("66d55a3c058ca86736643dc8c9f26b7555a5aa5b"))
            assertTrue(it.contains("b87b317bc80c5773d2a1ab83390e8849e50b098b"))
        }

        File(pluginDir, "66d55a3c058ca86736643dc8c9f26b7555a5aa5b").list()?.let {
            assertTrue(it.contains("com.intellij.lang.jsgraphql-3.1.3.zip"))
        }

        File(pluginDir, "b87b317bc80c5773d2a1ab83390e8849e50b098b").list()?.let {
            assertTrue(it.contains("com.intellij.lang.jsgraphql-3.1.3.pom"))
        }
    }

    @Test
    fun `download zip plugin from non-default channel`() {
        buildFile.groovy("""
            intellij {
                plugins = ["io.flutter:67.0.2-dev.1@dev"]
            }
        """)

        build(BasePlugin.ASSEMBLE_TASK_NAME)
        val pluginDir = File(pluginsNightlyRepositoryCacheDir, "dev.com.jetbrains.plugins/io.flutter/67.0.2-dev.1")

        pluginDir.list()?.let {
            assertTrue(it.contains("9cab70cc371b245cd808ade65630f505a6443b0d"))
        }

        File(pluginDir, "9cab70cc371b245cd808ade65630f505a6443b0d").list()?.let {
            assertTrue(it.contains("io.flutter-67.0.2-dev.1.zip"))
        }

        pluginsDevCacheDir.list()?.let {
            assertTrue(it.contains("io.flutter-67.0.2-dev.1"))
        }
    }

    @Test
    fun `download zip plugin`() {
        buildFile.groovy("""
            intellij {
                plugins = ["org.intellij.plugins.markdown:$testMarkdownPluginVersion"]
            }
        """)

        build(BasePlugin.ASSEMBLE_TASK_NAME)
        val pluginDir = File(pluginsRepositoryCacheDir, "com.jetbrains.plugins/org.intellij.plugins.markdown/$testMarkdownPluginVersion")

        pluginDir.list()?.let {
            assertTrue(it.contains("17328855fcd031f39a805db934c121eaa25dedfb"))
        }

        File(pluginDir, "17328855fcd031f39a805db934c121eaa25dedfb").list()?.let {
            assertTrue(it.contains("org.intellij.plugins.markdown-$testMarkdownPluginVersion.zip"))
        }

        File(pluginsCacheDir, "unzipped.com.jetbrains.plugins").list()?.let {
            assertTrue(it.contains("org.intellij.plugins.markdown-$testMarkdownPluginVersion"))
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
