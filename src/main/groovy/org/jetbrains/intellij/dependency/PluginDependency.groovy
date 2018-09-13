package org.jetbrains.intellij.dependency

import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

interface PluginDependency {
    @NotNull
    String getId()

    @NotNull
    String getVersion()

    @Nullable
    String getChannel()

    @NotNull
    File getArtifact()

    @NotNull
    Collection<File> getJarFiles()

    @Nullable
    File getClassesDirectory()

    @Nullable
    File getMetaInfDirectory()

    @Nullable
    File getSourcesDirectory()

    boolean isBuiltin()

    boolean isMaven()
}