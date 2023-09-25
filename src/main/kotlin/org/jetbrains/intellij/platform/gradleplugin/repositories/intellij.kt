// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("unused")

package org.jetbrains.intellij.platform.gradleplugin.repositories

import org.gradle.api.artifacts.dsl.RepositoryHandler

fun RepositoryHandler.intellij(action: Action = {}) = customRepository(
    name = "IntelliJ Repository",
    url = "https://www.jetbrains.com/intellij-repository/releases",
    urlWithCacheRedirector = "https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases",
    action = action,
)

fun RepositoryHandler.intellijSnapshots(action: Action = {}) = customRepository(
    name = "IntelliJ Repository (Snapshots)",
    url = "https://www.jetbrains.com/intellij-repository/snapshots",
    urlWithCacheRedirector = "https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/snapshots",
    action = action,
)

fun RepositoryHandler.intellijNightly(action: Action = {}) = customRepository(
    name = "IntelliJ Repository (Nightly)",
    url = "https://www.jetbrains.com/intellij-repository/nightly",
    urlWithCacheRedirector = "https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/nightly",
    action = action,
)
