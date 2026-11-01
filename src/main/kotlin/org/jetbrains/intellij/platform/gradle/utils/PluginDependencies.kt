// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.plugin.ModuleDescriptor

/**
 * Returns non-optional plugin and module dependency IDs from the main descriptor and selected content modules.
 * Callers decide which content-module loading rules are required in their context.
 */
internal fun IdePlugin.requiredDependencyIds(includeModule: (ModuleDescriptor) -> Boolean): Sequence<String> =
    (sequenceOf(this) + modulesDescriptors.asSequence().filter(includeModule).map { it.module })
        .flatMap { module ->
            module.dependsList.asSequence().filterNot { it.isOptional }.map { it.pluginId } +
                module.pluginMainModuleDependencies.asSequence().map { it.pluginId } +
                module.contentModuleDependencies.asSequence().map { it.moduleName }
        }
        .filter(String::isNotBlank)
        .distinct()