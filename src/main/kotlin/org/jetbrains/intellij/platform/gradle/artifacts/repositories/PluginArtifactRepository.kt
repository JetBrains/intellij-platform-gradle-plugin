// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.repositories

import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.UrlArtifactRepository
import org.gradle.api.credentials.Credentials

interface PluginArtifactRepository : ArtifactRepository, UrlArtifactRepository {
    fun <T : Credentials?> credentials(credentialsType: Class<T>, action: Action<in T>)

    fun <T : Credentials?> getCredentials(credentialsType: Class<T>): T?
}

inline fun <reified T : Credentials?> PluginArtifactRepository.credentials(action: Action<in T>) {
    credentials(T::class.java, action)
}
