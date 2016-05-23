package org.jetbrains.intellij.dependency

import com.google.common.base.Predicate
import com.intellij.structure.impl.utils.JarsUtils
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class IdeaDependency {
    @NotNull
    private final String buildNumber
    @NotNull
    private final File classes
    @Nullable
    private final File sources
    @NotNull
    private final Collection<File> jarFiles
    private final boolean withKotlin

    IdeaDependency(@NotNull String buildNumber, @NotNull File classes, @Nullable File sources, boolean withKotlin) {
        this.buildNumber = buildNumber
        this.classes = classes
        this.sources = sources
        this.withKotlin = withKotlin
        jarFiles = collectJarFiles()
    }

    private def collectJarFiles() {
        if (classes.isDirectory()) {
            File lib = new File(classes, "lib");
            if (lib.isDirectory()) {
                return JarsUtils.collectJars(lib, new Predicate<File>() {
                    @Override
                    boolean apply(File file) {
                        return withKotlin || "kotlin-runtime.jar" != file.name && "kotlin-reflect.jar" != file.name
                    }
                }, false)
            }
        }
        return Collections.emptySet()
    }

    @NotNull
    String getBuildNumber() {
        return buildNumber
    }

    @NotNull
    File getClasses() {
        return classes
    }

    @Nullable
    File getSources() {
        return sources
    }

    @NotNull
    Collection<File> getJarFiles() {
        return jarFiles
    }

    String getFqn() {
        def fqn = "idea$buildNumber"
        if (withKotlin) {
            fqn += '-withKotlin'
        }
        if (sources) {
            fqn += '-withSources'
        }
        return fqn
    }
}
