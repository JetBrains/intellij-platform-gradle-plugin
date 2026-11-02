// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.newInstance
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.ProductMode
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependencyConfiguration
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RequestedIntelliJPlatformsServiceTest {

    private val project = ProjectBuilder.builder().build()
    private val objects = project.objects
    private val providers = project.providers
    private val extensionProvider: Provider<IntelliJPlatformExtension> = providers.provider { error("Unused") }

    @Test
    fun `base IntelliJ Platform request can be registered repeatedly with the same value`() {
        val service = service()
        val configuration = configuration()
        val expected = RequestedIntelliJPlatform(
            type = IntelliJPlatformType.IntellijIdeaUltimate,
            version = "2026.1.2",
            useInstaller = true,
            useCache = false,
            productMode = ProductMode.MONOLITH,
        )

        assertEquals(expected, service.set(configuration).get())
        assertEquals(expected, service.set(configuration).get())
    }

    @Test
    fun `base IntelliJ Platform request fails when registered again with a different value`() {
        val service = service()

        service.set(configuration()).get()
        val exception = assertFailsWith<IllegalStateException> {
            service.set(configuration(type = IntelliJPlatformType.IntellijIdeaCommunity)).get()
        }

        assertContains(
            exception.message.orEmpty(),
            "The 'intellijPlatformDependency' configuration already contains the following IntelliJ Platform dependency: IU-2026.1.2 [useInstaller=true, useCache=false, productMode=MONOLITH]",
        )
    }

    @Test
    fun `raw declarations retain latest and do not evaluate artifact settings`() {
        val service = service()
        val configuration = configuration(version = "latest").apply {
            useInstaller.set(providers.provider { error("Installer settings must not be observed") })
            useCache.set(providers.provider { error("Cache settings must not be observed") })
        }
        service.recordDeclaredIntelliJPlatforms(listOf(configuration), Configurations.INTELLIJ_PLATFORM_DEPENDENCY)

        val declaration = service.declaredIntelliJPlatforms(Configurations.INTELLIJ_PLATFORM_DEPENDENCY).single()
        assertSame(configuration, declaration)
        assertEquals("latest", declaration.version.get())
        assertTrue(service.declaredIntelliJPlatforms(Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY).isEmpty())
    }

    @Test
    fun `raw declarations append without conflating base and verifier targets`() {
        val service = service()
        val base = configuration()
        val verifier = configuration(version = "2025.3")
        service.recordDeclaredIntelliJPlatforms(listOf(base), Configurations.INTELLIJ_PLATFORM_DEPENDENCY)
        service.recordDeclaredIntelliJPlatforms(listOf(verifier), Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY)

        assertEquals(listOf(base), service.declaredIntelliJPlatforms(Configurations.INTELLIJ_PLATFORM_DEPENDENCY))
        assertEquals(listOf(verifier), service.declaredIntelliJPlatforms(Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY))
    }

    @Test
    fun `Rider requests default to non-installer distributions`() {
        val service = service()
        val configuration = configuration(type = IntelliJPlatformType.Rider, version = "2024.3.2", useInstaller = null)

        assertEquals(false, service.request(configuration).get().useInstaller)
        assertEquals(false, service.request(configuration, Configurations.INTELLIJ_PLATFORM_DEPENDENCY).get().useInstaller)
        assertEquals(false, service.request(configuration, "custom").get().useInstaller)
        assertEquals(false, configuration.useInstaller.isPresent)
    }

    @Test
    fun `Rider requests respect explicit installer settings`() {
        val service = service()

        for (useInstaller in listOf(true, false)) {
            val configuration = configuration(type = IntelliJPlatformType.Rider, useInstaller = useInstaller)
            assertEquals(useInstaller, service.request(configuration).get().useInstaller)
            assertEquals(useInstaller, service.request(configuration, "custom").get().useInstaller)

            configuration.useInstaller.set(providers.provider { useInstaller })
            assertEquals(useInstaller, service.request(configuration).get().useInstaller)
            assertEquals(useInstaller, service.request(configuration, "custom").get().useInstaller)
        }
    }

    @Test
    fun `other products retain the service installer default`() {
        for (useInstaller in listOf(true, false)) {
            val service = service(useInstaller)
            val configuration = configuration(useInstaller = null)

            assertEquals(useInstaller, service.request(configuration).get().useInstaller)
            assertEquals(useInstaller, service.request(configuration, "custom").get().useInstaller)
        }
    }

    @Test
    fun `named requests inherit base installer settings unless explicitly overridden`() {
        for (type in listOf(IntelliJPlatformType.Rider, IntelliJPlatformType.IntellijIdeaUltimate)) {
            for (useInstaller in listOf(null, true, false)) {
                val service = service()
                val base = service.set(configuration(type = type, useInstaller = useInstaller)).get()
                val configuration = configuration(useInstaller = null).apply {
                    this.type.unset()
                    version.unset()
                }

                assertEquals(base, service.request(configuration, "custom").get())
                configuration.type.set(IntelliJPlatformType.IntellijIdeaCommunity)
                assertEquals(base.useInstaller, service.request(configuration, "custom").get().useInstaller)

                configuration.useInstaller.set(!base.useInstaller)
                assertEquals(!base.useInstaller, service.request(configuration, "custom").get().useInstaller)
            }
        }
    }

    @Test
    fun `installer defaults evaluate the platform type lazily`() {
        val service = service()
        val configuration = configuration(useInstaller = null).apply {
            type.set(providers.provider { error("Type must not be queried during request construction") })
        }
        val request = service.request(configuration)
        val namedRequest = service.request(configuration, "custom")

        configuration.type.set(providers.provider { IntelliJPlatformType.Rider })

        assertEquals(false, request.get().useInstaller)
        assertEquals(false, namedRequest.get().useInstaller)
    }

    @Test
    fun `incomplete named requests retain missing type and version errors`() {
        val service = service()
        val configuration = configuration(type = IntelliJPlatformType.Rider, useInstaller = null)
        configuration.type.unset()

        for (property in listOf("type", "version")) {
            val exception = assertFailsWith<IllegalStateException> {
                service.request(configuration, "custom").get()
            }
            assertContains(exception.message.orEmpty(), "does not specify the $property of the IntelliJ Platform dependency")
            configuration.type.set(IntelliJPlatformType.Rider)
            configuration.version.unset()
        }
    }

    private fun service(useInstaller: Boolean = true): RequestedIntelliJPlatformsService {
        val parameters = objects.newInstance<RequestedIntelliJPlatformsService.Parameters>().apply {
            this.useInstaller.set(useInstaller)
        }

        return object : RequestedIntelliJPlatformsService(objects, providers) {
            override fun getParameters() = parameters
        }
    }

    private fun configuration(
        type: IntelliJPlatformType = IntelliJPlatformType.IntellijIdeaUltimate,
        version: String = "2026.1.2",
        useInstaller: Boolean? = true,
        useCache: Boolean = false,
        productMode: ProductMode = ProductMode.MONOLITH,
    ) = objects.newInstance<IntelliJPlatformDependencyConfiguration>(objects, extensionProvider).apply {
        this.type.set(type)
        this.version.set(version)
        this.useInstaller.set(useInstaller)
        this.useCache.set(useCache)
        this.productMode.set(productMode)
    }
}
