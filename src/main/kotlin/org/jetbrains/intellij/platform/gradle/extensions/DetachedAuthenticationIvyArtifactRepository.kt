// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.credentials.Credentials

internal class DetachedAuthenticationIvyArtifactRepository(
    repository: IvyArtifactRepository,
    private val authenticationRepository: IvyArtifactRepository,
) : IvyArtifactRepository by repository {

    override fun getCredentials() = authenticationRepository.getCredentials()

    override fun <T : Credentials> getCredentials(credentialsType: Class<T>) = authenticationRepository.getCredentials(credentialsType)

    override fun <T : Credentials> credentials(credentialsType: Class<T>, action: Action<in T>) =
        authenticationRepository.credentials(credentialsType, action)

    override fun credentials(action: Action<in PasswordCredentials>) = authenticationRepository.credentials(action)

    override fun credentials(credentialsType: Class<out Credentials>) = authenticationRepository.credentials(credentialsType)

    override fun getAuthentication() = authenticationRepository.getAuthentication()

    override fun authentication(action: Action<in AuthenticationContainer>) = authenticationRepository.authentication(action)
}
