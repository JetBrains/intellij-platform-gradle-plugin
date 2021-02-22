package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

@CompileStatic
interface PluginDependency extends Serializable {
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

    boolean isCompatible(@NotNull IdeVersion ideVersion)

    PluginDependencyNotation getNotation()
}