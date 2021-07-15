package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SignPluginTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `output file contains version when specified in build file`() {
        buildFile.groovy("""
            version '1.0.0'
            signPlugin {
                certificateChain.set(file("${loadCertFile("certificates/cert.crt")}").text)
                privateKey.set(file("${loadCertFile("certificates/cert.key")}").text)
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

    private fun loadCertFile(name: String) = javaClass.classLoader.getResource(name)?.file
}
