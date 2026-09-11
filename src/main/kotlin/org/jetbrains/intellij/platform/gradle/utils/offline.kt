// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import org.gradle.api.GradleException

/**
 * Builds a single, consistent message used whenever a required artifact cannot be resolved because Gradle runs in
 * offline mode (`--offline` / [org.gradle.StartParameter.isOffline]) and the artifact is not present in the local
 * cache.
 *
 * The message names what is missing and how to make it available, so it stays actionable across all custom
 * out-of-band resolution paths (product releases, installer download URLs, `latest`/`LATEST-EAP-SNAPSHOT`
 * resolution, Marketplace shim, maven-metadata lookups).
 *
 * @param what a human-readable description of the missing artifact, typically its coordinates (e.g. `idea:ideaIC:2024.1`).
 */
internal fun offlineResolutionErrorMessage(what: String) =
    "No cached version of $what available for offline mode. " +
            "Run the build once without the `--offline` flag to populate the cache, then retry."

/**
 * Throws a [GradleException] with the unified [offlineResolutionErrorMessage] for the given [what].
 *
 * @see offlineResolutionErrorMessage
 */
internal fun offlineResolutionError(what: String): Nothing =
    throw GradleException(offlineResolutionErrorMessage(what))
