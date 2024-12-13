// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.repositories

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.AuthenticationSupported
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.credentials.Credentials
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.property
import org.jetbrains.intellij.platform.gradle.CustomPluginRepositoryType
import org.jetbrains.intellij.platform.gradle.shim.Shim
import java.net.URI
import javax.inject.Inject

/**
 * Represents a repository used for handling custom plugin repositories with authentication support.
 *
 * @param objects Factory for creating domain objects.
 * @param instantiator The Gradle instantiator used to create new credential instances.
 * @param name The name of the repository.
 * @param url The URL of the repository.
 * @param type The custom repository type for plugins.
 * @param allowInsecureProtocol Flag indicating if insecure protocols (like http) are allowed.
 *
 * @see CustomPluginRepositoryType
 * @see Shim
 */
abstract class PluginArtifactRepository @Inject constructor(
    objects: ObjectFactory,
    private val instantiator: Instantiator,
    name: String,
    url: URI,
    val type: CustomPluginRepositoryType,
    allowInsecureProtocol: Boolean,
) : BaseArtifactRepository(name, url, allowInsecureProtocol), AuthenticationSupported {

    private val credentials = objects.property(Credentials::class)

    override fun getCredentials() = getCredentials(PasswordCredentials::class.java)

    /**
     * Retrieves the credentials of the specified type for the repository.
     *
     * @param T The type of credentials to retrieve, which must be a subclass of [Credentials].
     * @param credentialsType The class of the credentials type to retrieve.
     * @return An instance of the requested credentials type.
     * @throws GradleException If the credentials are missing or if they cannot be cast to the specified type.
     */
    @Throws(GradleException::class)
    override fun <T : Credentials> getCredentials(credentialsType: Class<T>): T =
        credentials.orNull?.let {
            when {
                credentialsType.isAssignableFrom(it.javaClass) -> credentialsType.cast(it)
                else -> null
            }
        } ?: throw GradleException("Missing credentials")

    /**
     * Configures the credentials for the repository.
     *
     * @param T The type of credentials to configure, which must be a subclass of [Credentials].
     * @param credentialsType The class of the credentials type to instantiate.
     * @param action The action used to configure the credentials.
     */
    override fun <T : Credentials?> credentials(credentialsType: Class<T>, action: Action<in T>) {
        val credentialsValue = requireNotNull(instantiateCredentials(credentialsType))
        action.execute(credentialsValue)
        credentials = credentialsValue
    }

    override fun credentials(action: Action<in PasswordCredentials>) = credentials(PasswordCredentials::class.java, action)

    override fun credentials(credentialsType: Class<out Credentials?>) = throw UnsupportedOperationException()

    /**
     * Instantiates a specific type of credentials based on the provided credential type.
     *
     * @param T The type of credentials to instantiate, which must be a subclass of [Credentials].
     * @param credentialType The class of the credential type to instantiate.
     * @throws IllegalArgumentException If the credential type is not recognized.
     * @return An instance of the requested credential type.
     */
    @Throws(IllegalArgumentException::class)
    private fun <T : Credentials?> instantiateCredentials(credentialType: Class<T>) = when {
        PasswordCredentials::class.java.isAssignableFrom(credentialType) -> credentialType.cast(instantiator.newInstance(PasswordCredentials::class.java))
        HttpHeaderCredentials::class.java.isAssignableFrom(credentialType) -> credentialType.cast(instantiator.newInstance(HttpHeaderCredentials::class.java))
        else -> throw IllegalArgumentException("Unrecognized credential type: ${credentialType.getName()}")
    }

    override fun getAuthentication() = throw UnsupportedOperationException()

    override fun authentication(action: Action<in AuthenticationContainer>) = throw UnsupportedOperationException()
}

/**
 * Configures the credentials for the [PluginArtifactRepository] using the provided action.
 *
 * @receiver Repository to configure.
 * @param T The type of `Credentials` being configured.
 * @param action The action used to configure the credentials.
 */
inline fun <reified T : Credentials?> PluginArtifactRepository.credentials(action: Action<in T>) {
    credentials(T::class.java, action)
}
