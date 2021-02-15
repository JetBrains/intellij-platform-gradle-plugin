package org.jetbrains.intellij.dependency

import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

interface PluginsRepository {
    @Nullable
    File resolve(@NotNull PluginDependencyNotation plugin)

    void postResolve()
}