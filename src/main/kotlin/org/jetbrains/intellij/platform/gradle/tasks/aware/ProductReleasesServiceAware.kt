// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.jetbrains.intellij.platform.gradle.services.ProductReleasesService

interface ProductReleasesServiceAware : IntelliJPlatformAware {

    @get:Internal
    val productReleasesService: Property<ProductReleasesService>
}
