/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.intellij.dsl

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject
import org.jetbrains.intellij.utils.nullableProperty
import org.jetbrains.intellij.utils.notNullProperty

private const val DEFAULT_PROGUARD_VERSION = "7.2.2"

abstract class ProguardSettings @Inject constructor(
    objects: ObjectFactory,
) {
    val version: Property<String> = objects.notNullProperty(DEFAULT_PROGUARD_VERSION)
    val maxHeapSize: Property<String?> = objects.nullableProperty()
    val configurationFiles: ConfigurableFileCollection = objects.fileCollection()
    val isEnabled: Property<Boolean> = objects.notNullProperty(false)
    val obfuscate: Property<Boolean> = objects.notNullProperty(false)
}
