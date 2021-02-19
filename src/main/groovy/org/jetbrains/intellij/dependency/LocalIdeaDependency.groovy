package org.jetbrains.intellij.dependency

import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class LocalIdeaDependency extends IdeaDependency {
    LocalIdeaDependency(@NotNull String name, @NotNull String version, @NotNull String buildNumber,
                        @NotNull File classes, @Nullable File sources, boolean withKotlin,
                        @NotNull BuiltinPluginsRegistry builtinPluginsRegistry,
                        @NotNull Collection<IdeaExtraDependency> extraDependencies) {
        super(name, version, buildNumber, classes, sources, withKotlin, builtinPluginsRegistry, extraDependencies)
    }

    File getIvyRepositoryDirectory() {
        version.endsWith(".SNAPSHOT") ? null : super.getIvyRepositoryDirectory()
    }
}
