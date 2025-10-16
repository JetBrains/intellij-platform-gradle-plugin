// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import org.gradle.api.provider.Provider

internal data class Quadruple<T1, T2, T3, T4>(val first: T1, val second: T2, val third: T3, val fourth: T4)

internal fun <A : Any, B : Any, C : Any, R : Any> zip(
    a: Provider<A>,
    b: Provider<B>,
    c: Provider<C>,
    combiner: (A, B, C) -> R,
): Provider<R> = a
    .zip(b) { av, bv -> av to bv }
    .zip(c) { (av, bv), cv -> combiner(av, bv, cv) }

internal fun <A : Any, B : Any, C : Any, D : Any, R : Any> zip(
    a: Provider<A>,
    b: Provider<B>,
    c: Provider<C>,
    d: Provider<D>,
    combiner: (A, B, C, D) -> R,
): Provider<R> = a
    .zip(b) { av, bv -> av to bv }
    .zip(c) { (av, bv), cv -> Triple(av, bv, cv) }
    .zip(d) { (av, bv, cv), dv -> combiner(av, bv, cv, dv) }

internal fun <A : Any, B : Any, C : Any, D : Any, E : Any, R : Any> zip(
    a: Provider<A>,
    b: Provider<B>,
    c: Provider<C>,
    d: Provider<D>,
    e: Provider<E>,
    combiner: (A, B, C, D, E) -> R,
): Provider<R> = a
    .zip(b) { av, bv -> av to bv }
    .zip(c) { (av, bv), cv -> Triple(av, bv, cv) }
    .zip(d) { (av, bv, cv), dv -> Quadruple(av, bv, cv, dv) }
    .zip(e) { (av, bv, cv, dv), ev -> combiner(av, bv, cv, dv, ev) }
