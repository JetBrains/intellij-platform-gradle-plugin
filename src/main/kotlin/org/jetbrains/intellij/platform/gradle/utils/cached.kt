// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.HasConfigurableValue
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalTypeInference
import kotlin.reflect.full.isSubclassOf

@OptIn(ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
inline fun <reified T : Any> Project.cachedProvider(
    crossinline block: () -> T?
): Provider<out T> =
    providers.cachedProvider(objects, block)

@JvmName("cachedListProvider")
inline fun <reified T : Any> Project.cachedProvider(
    crossinline block: () -> List<T>?
): Provider<out List<T>> =
    providers.cachedProvider(objects, block)

@JvmName("cachedMapProvider")
inline fun <reified K : Any, reified V : Any> Project.cachedProvider(
    crossinline block: () -> Map<K, V>?
): Provider<out Map<K, V>> =
    providers.cachedProvider(objects, block)

@JvmName("cachedSetProvider")
inline fun <reified T : Any> Project.cachedProvider(
    crossinline block: () -> Set<T>?
): Provider<out Set<T>> =
    providers.cachedProvider(objects, block)

@OptIn(ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
inline fun <reified T : Any> ProviderFactory.cachedProvider(
    objects: ObjectFactory,
    crossinline block: () -> T?
): Provider<out T> =
    provider { block() }.cached(objects)

@JvmName("cachedListProvider")
inline fun <reified T : Any> ProviderFactory.cachedProvider(
    objects: ObjectFactory,
    crossinline block: () -> List<T>?
): Provider<out List<T>> =
    provider { block() }.cached(objects)

@JvmName("cachedMapProvider")
inline fun <reified K : Any, reified V : Any> ProviderFactory.cachedProvider(
    objects: ObjectFactory,
    crossinline block: () -> Map<K, V>?
): Provider<out Map<K, V>> =
    provider { block() }.cached(objects)

@JvmName("cachedSetProvider")
inline fun <reified T : Any> ProviderFactory.cachedProvider(
    objects: ObjectFactory,
    crossinline block: () -> Set<T>?
): Provider<out Set<T>> =
    provider { block() }.cached(objects)

@OptIn(ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
inline fun <T : Any, reified S : Any> Provider<out T>.cachedMap(
    project: Project,
    crossinline transformer: (T) -> S?
): Provider<out S> =
    cachedMap(project.objects, transformer)

@JvmName("cachedMapToList")
inline fun <T : Any, reified S : Any> Provider<out T>.cachedMap(
    project: Project,
    crossinline transformer: (T) -> List<S>?
): Provider<out List<S>> =
    cachedMap(project.objects, transformer)

@JvmName("cachedMapToMap")
inline fun <T : Any, reified K : Any, reified V : Any> Provider<out T>.cachedMap(
    project: Project,
    crossinline transformer: (T) -> Map<K, V>?
): Provider<out Map<K, V>> =
    cachedMap(project.objects, transformer)

@JvmName("cachedMapToSet")
inline fun <T : Any, reified S : Any> Provider<out T>.cachedMap(
    project: Project,
    crossinline transformer: (T) -> Set<S>?
): Provider<out Set<S>> =
    cachedMap(project.objects, transformer)

@OptIn(ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
inline fun <T : Any, reified S : Any> Provider<out T>.cachedMap(
    objects: ObjectFactory,
    crossinline transformer: (T) -> S?
): Provider<out S> =
    map { transformer(it).markedAsNonNull }.cached(objects)

@JvmName("cachedMapToList")
inline fun <T : Any, reified S : Any> Provider<out T>.cachedMap(
    objects: ObjectFactory,
    crossinline transformer: (T) -> List<S>?
): Provider<out List<S>> =
    map { transformer(it).markedAsNonNull }.cached(objects)

@JvmName("cachedMapToMap")
inline fun <T : Any, reified K : Any, reified V : Any> Provider<out T>.cachedMap(
    objects: ObjectFactory,
    crossinline transformer: (T) -> Map<K, V>?
): Provider<out Map<K, V>> =
    map { transformer(it).markedAsNonNull }.cached(objects)

@JvmName("cachedMapToSet")
inline fun <T : Any, reified S : Any> Provider<out T>.cachedMap(
    objects: ObjectFactory,
    crossinline transformer: (T) -> Set<S>?
): Provider<out Set<S>> =
    map { transformer(it).markedAsNonNull }.cached(objects)

@OptIn(ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
inline fun <T : Any, reified S : Any> Provider<out T>.cachedFlatMap(
    project: Project,
    crossinline transformer: (T) -> Provider<out S>?
): Provider<out S> =
    cachedFlatMap(project.objects, transformer)

@JvmName("cachedFlatMapToList")
inline fun <T : Any, reified S : Any> Provider<out T>.cachedFlatMap(
    project: Project,
    crossinline transformer: (T) -> Provider<out List<S>>?
): Provider<out List<S>> =
    cachedFlatMap(project.objects, transformer)

@JvmName("cachedFlatMapToMap")
inline fun <T : Any, reified K : Any, reified V : Any> Provider<out T>.cachedFlatMap(
    project: Project,
    crossinline transformer: (T) -> Provider<out Map<K, V>>?
): Provider<out Map<K, V>> =
    cachedFlatMap(project.objects, transformer)

@JvmName("cachedFlatMapToSet")
inline fun <T : Any, reified S : Any> Provider<out T>.cachedFlatMap(
    project: Project,
    crossinline transformer: (T) -> Provider<out Set<S>>?
): Provider<out Set<S>> =
    cachedFlatMap(project.objects, transformer)

@OptIn(ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
inline fun <T : Any, reified S : Any> Provider<out T>.cachedFlatMap(
    objects: ObjectFactory,
    crossinline transformer: (T) -> Provider<out S>?
): Provider<out S> =
    flatMap { transformer(it).markedAsNonNull }.cached(objects)

@JvmName("cachedFlatMapToList")
inline fun <T : Any, reified S : Any> Provider<out T>.cachedFlatMap(
    objects: ObjectFactory,
    crossinline transformer: (T) -> Provider<out List<S>>?
): Provider<out List<S>> =
    flatMap { transformer(it).markedAsNonNull }.cached(objects)

@JvmName("cachedFlatMapToMap")
inline fun <T : Any, reified K : Any, reified V : Any> Provider<out T>.cachedFlatMap(
    objects: ObjectFactory,
    crossinline transformer: (T) -> Provider<out Map<K, V>>?
): Provider<out Map<K, V>> =
    flatMap { transformer(it).markedAsNonNull }.cached(objects)

@JvmName("cachedFlatMapToSet")
inline fun <T : Any, reified S : Any> Provider<out T>.cachedFlatMap(
    objects: ObjectFactory,
    crossinline transformer: (T) -> Provider<out Set<S>>?
): Provider<out Set<S>> =
    flatMap { transformer(it).markedAsNonNull }.cached(objects)

@OptIn(ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
inline fun <T : Any, U : Any, reified R : Any> Provider<out T>.cachedZip(
    project: Project,
    right: Provider<out U>,
    crossinline combiner: (T, U) -> R?
): Provider<out R> =
    cachedZip(project.objects, right, combiner)

@JvmName("cachedZipToList")
inline fun <T : Any, U : Any, reified R : Any> Provider<out T>.cachedZip(
    project: Project,
    right: Provider<out U>,
    crossinline combiner: (T, U) -> List<R>?
): Provider<out List<R>> =
    cachedZip(project.objects, right, combiner)

@JvmName("cachedZipToMap")
inline fun <T : Any, U : Any, reified K : Any, reified V : Any> Provider<out T>.cachedZip(
    project: Project,
    right: Provider<out U>,
    crossinline combiner: (T, U) -> Map<K, V>?
): Provider<out Map<K, V>> =
    cachedZip(project.objects, right, combiner)

@JvmName("cachedZipToSet")
inline fun <T : Any, U : Any, reified R : Any> Provider<out T>.cachedZip(
    project: Project,
    right: Provider<out U>,
    crossinline combiner: (T, U) -> Set<R>?
): Provider<out Set<R>> =
    cachedZip(project.objects, right, combiner)

@OptIn(ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
inline fun <T : Any, U : Any, reified R : Any> Provider<out T>.cachedZip(
    objects: ObjectFactory,
    right: Provider<out U>,
    crossinline combiner: (T, U) -> R?
): Provider<out R> =
    zip(right) { leftValue, rightValue -> combiner(leftValue, rightValue) }.cached(objects)

@JvmName("cachedZipToList")
inline fun <T : Any, U : Any, reified R : Any> Provider<out T>.cachedZip(
    objects: ObjectFactory,
    right: Provider<out U>,
    crossinline combiner: (T, U) -> List<R>?
): Provider<out List<R>> =
    zip(right) { leftValue, rightValue -> combiner(leftValue, rightValue) }.cached(objects)

@JvmName("cachedZipToMap")
inline fun <T : Any, U : Any, reified K : Any, reified V : Any> Provider<out T>.cachedZip(
    objects: ObjectFactory,
    right: Provider<out U>,
    crossinline combiner: (T, U) -> Map<K, V>?
): Provider<out Map<K, V>> =
    zip(right) { leftValue, rightValue -> combiner(leftValue, rightValue) }.cached(objects)

@JvmName("cachedZipToSet")
inline fun <T : Any, U : Any, reified R : Any> Provider<out T>.cachedZip(
    objects: ObjectFactory,
    right: Provider<out U>,
    crossinline combiner: (T, U) -> Set<R>?
): Provider<out Set<R>> =
    zip(right) { leftValue, rightValue -> combiner(leftValue, rightValue) }.cached(objects)

inline fun <reified T : Any> Provider<out T>.cached(
    project: Project
): Provider<out T> =
    cached(project.objects)

@JvmName("cachedList")
inline fun <reified T : Any> Provider<out List<T>>.cached(
    project: Project
): Provider<out List<T>> =
    cached(project.objects)

@JvmName("cachedMap")
inline fun <reified K : Any, reified V : Any> Provider<out Map<K, V>>.cached(
    project: Project
): Provider<out Map<K, V>> =
    cached(project.objects)

@JvmName("cachedSet")
inline fun <reified T : Any> Provider<out Set<T>>.cached(
    project: Project
): Provider<out Set<T>> =
    cached(project.objects)

inline fun <reified T : Any> Provider<out T>.cached(
    objects: ObjectFactory
): Provider<out T> =
    @Suppress("UNCHECKED_CAST")
    when {
        T::class.isSubclassOf(RegularFile::class) -> objects.fileProperty() as Property<T>
        T::class.isSubclassOf(Directory::class) -> objects.directoryProperty() as Property<T>
        else -> objects.property()
    }
        .value(this)
        .asImmutable()

@JvmName("cachedList")
inline fun <reified T : Any> Provider<out List<T>>.cached(
    objects: ObjectFactory
): Provider<out List<T>> =
    objects
        .listProperty<T>()
        .value(this)
        .asImmutable()

@JvmName("cachedMap")
inline fun <reified K : Any, reified V : Any> Provider<out Map<K, V>>.cached(
    objects: ObjectFactory
): Provider<out Map<K, V>> =
    objects
        .mapProperty<K, V>()
        .value(this)
        .asImmutable()

@JvmName("cachedSet")
inline fun <reified T : Any> Provider<out Set<T>>.cached(
    objects: ObjectFactory
): Provider<out Set<T>> =
    objects
        .setProperty<T>()
        .value(this)
        .asImmutable()

fun <T : HasConfigurableValue> T.asImmutable() = apply {
    disallowChanges()
    finalizeValueOnRead()
}

// work-around for https://github.com/gradle/gradle/issues/12388
@OptIn(ExperimentalContracts::class)
val <T> T?.markedAsNonNull: T
    get() {
        markAsNonNull()
        return this
    }

@OptIn(ExperimentalContracts::class)
fun <T> T?.markAsNonNull() {
    contract {
        returns() implies (this@markAsNonNull != null)
    }
}
