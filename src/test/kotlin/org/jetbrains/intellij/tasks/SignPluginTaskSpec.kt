package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignPluginTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `run Marketplace ZIP Signer in specified version`() {
        buildFile.groovy("""
            version = "1.0.0"
            
            signPlugin {
                cliVersion = "0.1.5"
                certificateChainFile = file("${loadCertFile("certificates/cert.crt")}")
                privateKeyFile = file("${loadCertFile("certificates/cert.key")}")
            }
        """)

        val result = build(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME)

        assertTrue(result.output.contains("Starting the IntelliJ Plugin Verifier 1.255"))
    }

    @Test
    fun `run Marketplace ZIP Signer fails on invalid version`() {
        buildFile.groovy("""
            version = "1.0.0"
            
            signPlugin {
                cliVersion = "0.0.1"
            }
        """)

        val result = buildAndFail(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME)

        assertTrue(result.output.contains("Could not find marketplace-zip-signer-0.0.1-cli.jar"))
    }

    @Test
    fun `output file contains version when specified in build file`() {
        buildFile.groovy("""
            version '1.0.0'
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

        assertTrue(result.output.contains("Reusing configuration cache."))
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

    private fun loadCertFile(name: String) = javaClass.classLoader.getResource(name)?.file
}
