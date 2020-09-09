package org.jetbrains.intellij.dependency

import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

interface PluginsRepository {

    @Nullable
    File resolve(@NotNull String id, @NotNull String version, @Nullable String channel)

    void postResolve()

}