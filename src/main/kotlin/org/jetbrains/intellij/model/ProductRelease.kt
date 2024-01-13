// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.model

import org.jetbrains.intellij.Version
import org.jetbrains.intellij.tasks.ListProductsReleasesTask

data class ProductRelease(

    val name: String,
    val type: String,
    val channel: ListProductsReleasesTask.Channel,
    val build: Version,
    val version: Version,
    val id: String,
)
