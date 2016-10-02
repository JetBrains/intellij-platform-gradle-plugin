package org.jetbrains.intellij.dependency

import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class JpsIdeaDependency extends IdeaDependency {
    public static final Set<String> ALLOWED_JAR_NAMES = ['jps-builders.jar', 'jps-model.jar', 'util.jar']

    JpsIdeaDependency(@NotNull String version, @NotNull String buildNumber, @NotNull File classes,
                      @Nullable File sources, boolean withKotlin) {
        super(version, buildNumber, classes, sources, withKotlin)
    }

    @Override
    protected Collection<File> collectJarFiles() {
        return super.collectJarFiles().findAll { ALLOWED_JAR_NAMES.contains(it.name) }
    }

    @Override
    String getFqn() {
        def fqn = "ideaJPS$version"
        if (sources) {
            fqn += '-withSources'
        }
        return fqn
    }
}
