package org.jetbrains.intellij.platform.gradle

import org.gradle.api.provider.ProviderFactory
import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GradlePropertiesTest {

    private lateinit var tempDir: Path
    private lateinit var providers: ProviderFactory
    private lateinit var defaultDir: Path

    private fun createTempDir(): Path = kotlin.io.path.createTempDirectory("gradle-properties-test")

    @BeforeTest
    fun setUp() {
        tempDir = createTempDir()
        val project = ProjectBuilder.builder().build()
        providers = project.providers
        defaultDir = tempDir.resolve("default")
    }

    @Test
    fun `resolveDir returns default directory when provider has no value`() {
        // Create a provider that has no value
        val emptyProvider = providers.provider { null as String? }

        val result = providers.resolveDir(emptyProvider, defaultDir)

        assertEquals(defaultDir.toAbsolutePath(), result)
    }

    @Test
    fun `resolveDir returns default directory when provider value is empty`() {
        // Create a provider with empty string
        val emptyProvider = providers.provider { "" }

        val result = providers.resolveDir(emptyProvider, defaultDir)

        assertEquals(defaultDir.toAbsolutePath(), result)
    }

    @Test
    fun `resolveDir returns default directory when provider value is blank`() {
        // Create a provider with blank string
        val blankProvider = providers.provider { "   " }

        val result = providers.resolveDir(blankProvider, defaultDir)

        assertEquals(defaultDir.toAbsolutePath(), result)
    }

    @Test
    fun `resolveDir expands tilde to user home directory`() {
        // Create a provider with tilde path
        val tildeProvider = providers.provider { "~/.intellijPlatform/cache" }

        val result = providers.resolveDir(tildeProvider, defaultDir)

        assertEquals(result.pathString, System.getProperty("user.home") + "/.intellijPlatform/cache")
    }

    @Test
    fun `resolveDir uses custom absolute path when provider has value`() {
        val customPath = tempDir.resolve("custom").pathString
        // Create a provider with custom path
        val customProvider = providers.provider { customPath }

        val result = providers.resolveDir(customProvider, defaultDir)

        assertEquals(tempDir.resolve("custom").toAbsolutePath(), result)
    }

    @Test
    fun `resolveDir creates directories if they don't exist`() {
        val customPath = tempDir.resolve("deep/nested/path").pathString
        // Create a provider with nested path
        val nestedProvider = providers.provider { customPath }

        val result = providers.resolveDir(nestedProvider, defaultDir)

        assertEquals(tempDir.resolve("deep/nested/path").toAbsolutePath(), result)
    }

    @Test
    fun `resolveDir handles tilde in relative path`() {
        // Create a provider with tilde in relative path
        val tildeRelativeProvider = providers.provider { "~/my-project/.cache/intellij" }

        val result = providers.resolveDir(tildeRelativeProvider, defaultDir)

        assertEquals(result.pathString, System.getProperty("user.home") + "/my-project/.cache/intellij")
    }
}
