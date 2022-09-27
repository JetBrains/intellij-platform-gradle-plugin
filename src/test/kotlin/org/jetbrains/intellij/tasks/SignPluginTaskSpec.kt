// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("GroovyUnusedAssignment")
class SignPluginTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `run Marketplace ZIP Signer in the latest version`() {
        buildFile.groovy("""
            version = "1.0.0"
            
            signPlugin {
                certificateChainFile = file("${loadCertFile("certificates/cert.crt")}")
                privateKeyFile = file("${loadCertFile("certificates/cert.key")}")
            }
        """)

        val version = SignPluginTask.resolveLatestVersion()
        val result = build(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME, "--info")
        assertContains("marketplace-zip-signer-cli-$version.jar", result.output)
    }

    @Test
    fun `run Marketplace ZIP Signer in specified version`() {
        buildFile.groovy("""
            version = "1.0.0"
            
            signPlugin {
                cliVersion = "0.1.7"
                certificateChainFile = file("${loadCertFile("certificates/cert.crt")}")
                privateKeyFile = file("${loadCertFile("certificates/cert.key")}")
            }
        """)

        val result = build(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME, "--info")
        assertContains("marketplace-zip-signer-cli-0.1.7.jar", result.output)
    }

    @Test
    fun `skip Marketplace ZIP Signer task if no key and certificateChain were provided`() {
        buildFile.groovy("""
            version = "1.0.0"
        """)

        val result = build(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME)
        assertContains("Task :${IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME} SKIPPED", result.output)
    }

    @Test
    fun `run Marketplace ZIP Signer and fail on invalid version`() {
        buildFile.groovy("""
            version = "1.0.0"
            
            signPlugin {
                cliVersion = "0.0.1"
            }
        """)

        val result = buildAndFail(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME)
        assertContains("Could not find org.jetbrains:marketplace-zip-signer-cli:0.0.1.", result.output)
    }

    @Test
    fun `output file contains version when specified in build file`() {
        buildFile.groovy("""
            version = "1.0.0"
            
            signPlugin {
                certificateChainFile = file("${loadCertFile("certificates/cert.crt")}")
                privateKeyFile = file("${loadCertFile("certificates/cert.key")}")
            }
        """)
        build(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME)

        val distributionFolder = File(buildDirectory, "distributions")
        assertTrue(distributionFolder.listFiles()?.asList()?.any {
            it.name.equals("projectName-1.0.0-signed.zip")
        } ?: false)
    }

    @Test
    fun `reuse configuration cache`() {
        build(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME, "--configuration-cache")
        val result = build(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME, "--configuration-cache")
        assertContains("Reusing configuration cache.", result.output)
    }

    @Test
    fun `ignore cache when optional parameter changes`() {
        build(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME, "--configuration-cache")
        assertTrue(
            build(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME, "--configuration-cache")
                .output.contains("Reusing configuration cache.")
        )

        buildFile.groovy("""
            version = "1.0.0"
            
            signPlugin {
                password = "foo"
            }
        """)
        assertFalse(
            build(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME, "--configuration-cache")
                .output.contains("Reusing configuration cache.")
        )
    }

    private fun loadCertFile(name: String) = javaClass.classLoader.getResource(name)?.path
}
