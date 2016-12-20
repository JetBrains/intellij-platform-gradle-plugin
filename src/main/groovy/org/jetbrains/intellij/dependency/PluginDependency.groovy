package org.jetbrains.intellij.dependency

import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

interface PluginDependency {
    @NotNull
    File getArtifact()

    @NotNull
    Collection<File> getJarFiles()

    @Nullable
    File getClassesDirectory()

    @Nullable
    File getMetaInfDirectory()

    boolean isBuiltin()
}