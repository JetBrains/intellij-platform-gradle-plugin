// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.repositories

import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.AuthenticationSupported
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.credentials.Credentials
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.property
import org.jetbrains.intellij.platform.gradle.CustomPluginRepositoryType
import java.net.URI
import javax.inject.Inject

abstract class PluginArtifactRepository @Inject constructor(
    objects: ObjectFactory,
    private val instantiator: Instantiator,
    name: String,
    url: URI,
    val type: CustomPluginRepositoryType,
    allowInsecureProtocol: Boolean = true,
) : BaseArtifactRepository(name, url, allowInsecureProtocol), AuthenticationSupported {

    private val credentials = objects.property(Credentials::class)

    override fun <T : Credentials> getCredentials(credentialsType: Class<T>): T =
        credentials.orNull?.let {
            when {
                credentialsType.isAssignableFrom(it.javaClass) -> credentialsType.cast(it)
                else -> null
            }
        } ?: throw MissingCredentialsException()

    override fun <T : Credentials?> credentials(credentialsType: Class<T>, action: Action<in T>) {
        val credentialsValue = instantiateCredentials(credentialsType)
        action.execute(credentialsValue)
        credentials = credentialsValue
    }

    private fun <T : Credentials?> instantiateCredentials(credentialType: Class<T>) = when {
        PasswordCredentials::class.java.isAssignableFrom(credentialType) -> credentialType.cast(instantiator.newInstance(PasswordCredentials::class.java))
        HttpHeaderCredentials::class.java.isAssignableFrom(credentialType) -> credentialType.cast(instantiator.newInstance(HttpHeaderCredentials::class.java))
        else -> throw IllegalArgumentException("Unrecognized credential type: ${credentialType.getName()}");
    }
}

class MissingCredentialsException : Throwable()

inline fun <reified T : Credentials?> PluginArtifactRepository.credentials(action: Action<in T>) {
    credentials(T::class.java, action)
}
