package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SignPluginTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `reuse configuration cache`() {
        build(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME, "--configuration-cache")
        val result = build(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME, "--configuration-cache")

        assertTrue(result.output.contains("Reusing configuration cache."))
    }

    @Test
    fun `output file contains version when specified in build file`() {
        buildFile.appendText("version '1.0.0' \n" +
                "signPlugin {" +
                "    certificateChain.set(provider { file(\"${javaClass.classLoader.getResource("certificates/cert.crt").file}\").text })\n" +
                "    privateKey.set(provider { file(\"${javaClass.classLoader.getResource("certificates/cert.key").file}\").text })\n" +
                "}")
        build(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME)

        val distributionFolder = File(buildDirectory, "distributions")
        assertTrue(distributionFolder.listFiles().asList().any { it.name.contains("1.0.0-signed.zip") })
    }
}
