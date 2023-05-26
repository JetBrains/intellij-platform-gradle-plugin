// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import org.gradle.api.internal.provider.AbstractMinimalProvider
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.api.provider.Provider

@Suppress("UnstableApiUsage")
internal fun <T> Provider<T>.memoize(): Provider<T> = when (this) {
    // ValueSource instances already memoize their value
    is DefaultValueSourceProviderFactory.ValueSourceProvider<*, *> -> this

    // cannot memoize something that isn't a ProviderInternal; pretty much everything *must*
    // be a ProviderInternal, as this is how internal state (dependencies, etc) are carried
    !is ProviderInternal<T> -> error("Expected ProviderInternal, got $this")
    else -> MemoizedProvider(this)
}

private class MemoizedProvider<T>(private val delegate: ProviderInternal<T>) : AbstractMinimalProvider<T>() {

    // guarantee at-most-once execution of original provider
    private val memoizedValue = lazy { delegate.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead) }

    // always the same type as Provider value we are memoizing
    override fun getType(): Class<T>? = delegate.type

    // the producer is from the source provider
    override fun getProducer() = delegate.producer

    override fun toString() = "memoized($delegate)"

    override fun calculateOwnValue(valueConsumer: ValueSupplier.ValueConsumer) = memoizedValue.value
}
