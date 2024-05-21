// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.helpers

import org.gradle.api.provider.ProviderFactory

internal class ProvidersHelper(
    private val providers: ProviderFactory,
) {

    internal inline fun <reified T : Any> of(crossinline value: () -> T) = providers.provider {
        value()
    }
}
