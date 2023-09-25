// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("unused")

package org.jetbrains.intellij.platform.gradleplugin.repositories

import org.gradle.api.artifacts.dsl.RepositoryHandler

fun RepositoryHandler.jetbrainsMarketplace(action: Action = {}) = customRepository(
    name = "JetBrains Marketplace Repository",
    url = "https://plugins.jetbrains.com/maven",
    urlWithCacheRedirector = "https://cache-redirector.jetbrains.com/plugins.jetbrains.com/maven",
    action = action,
)
