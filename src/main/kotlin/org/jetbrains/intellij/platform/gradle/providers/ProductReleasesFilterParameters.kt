// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel

/**
 * Filter parameters used for selecting IntelliJ Platform product releases.
 */
interface ProductReleasesFilterParameters {
    /**
     * The build number from which the binary IDE releases will be matched.
     */
    @get:Input
    @get:Optional
    val sinceBuild: Property<String>

    /**
     * Build number until which the binary IDE releases will be matched.
     */
    @get:Input
    @get:Optional
    val untilBuild: Property<String>

    /**
     * A list of [IntelliJPlatformType] types to match.
     */
    @get:Input
    @get:Optional
    val types: ListProperty<IntelliJPlatformType>

    /**
     * A list of [Channel] types of binary releases to search in.
     */
    @get:Input
    @get:Optional
    val channels: ListProperty<Channel>
}
