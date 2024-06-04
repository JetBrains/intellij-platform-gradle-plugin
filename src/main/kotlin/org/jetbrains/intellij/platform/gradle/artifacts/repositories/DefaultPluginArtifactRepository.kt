// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.repositories

import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor
import org.gradle.api.credentials.Credentials
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.property
import java.net.URI
import javax.inject.Inject

abstract class DefaultPluginArtifactRepository @Inject constructor(
    objects: ObjectFactory,
    private val instantiator: Instantiator,
) : PluginArtifactRepository {

    private val credentials = objects.property(Credentials::class)
    private var allowInsecureProtocol = true
    private var name: String = ""
    private var url = URI("https://plugins.jetbrains.com") // TODO: Don't use Marketplace

    override fun getName() = name

    override fun setName(name: String) {
        this.name = name
    }

    override fun content(configureAction: Action<in RepositoryContentDescriptor>) {
        TODO("Not yet implemented")
    }

    override fun getUrl() = url

    override fun setUrl(url: URI) {
        this.url = url
    }

    override fun setUrl(url: Any) {
        TODO("Not yet implemented")
    }

    override fun isAllowInsecureProtocol() = allowInsecureProtocol

    override fun setAllowInsecureProtocol(allowInsecureProtocol: Boolean) {
        this.allowInsecureProtocol = allowInsecureProtocol
    }

    override fun <T : Credentials?> getCredentials(credentialsType: Class<T>): T? =
        credentials.orNull?.let {
            when {
                credentialsType.isAssignableFrom(it.javaClass) -> credentialsType.cast(it)
                else -> null
            }
        }

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
