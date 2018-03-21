package org.jetbrains.intellij.dependency

import com.google.common.base.Predicate
import groovy.transform.ToString
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.Utils

@ToString(includeNames = true, includeFields = true, ignoreNulls = true)
class IdeaDependency implements Serializable {
    @NotNull
    private final String name
    @NotNull
    private final String version
    @NotNull
    private final String buildNumber
    @NotNull
    private final File classes
    @Nullable
    private final File sources
    @NotNull
    private final Collection<File> jarFiles
    private final boolean withKotlin
    private final Collection<IdeaExtraDependency> extraDependencies

    IdeaDependency(@NotNull String name, @NotNull String version, @NotNull String buildNumber, @NotNull File classes,
                   @Nullable File sources, boolean withKotlin, Collection<IdeaExtraDependency> extraDependencies) {
        this.name = name
        this.version = version
        this.buildNumber = buildNumber
        this.classes = classes
        this.sources = sources
        this.withKotlin = withKotlin
        this.jarFiles = collectJarFiles()
        this.extraDependencies = extraDependencies
    }

    protected Collection<File> collectJarFiles() {
        if (classes.isDirectory()) {
            File lib = new File(classes, "lib")
            if (lib.isDirectory()) {
                return Utils.collectJars(lib, new Predicate<File>() {
                    @Override
                    boolean apply(File file) {
                        return withKotlin || IdeaDependencyManager.isKotlinRuntime(file.name - '.jar')
                    }
                }, false)
            }
        }
        return Collections.emptySet()
    }

    @NotNull
    String getName() {
        return name
    }

    @NotNull
    String getVersion() {
        return version
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

    boolean isWithKotlin() {
        return withKotlin
    }

    @NotNull
    Collection<IdeaExtraDependency> getExtraDependencies() {
        return extraDependencies
    }

    @Nullable
    File getIvyRepositoryDirectory() {
        classes
    }

    String getFqn() {
        def fqn = "$name-$version"
        if (withKotlin) {
            fqn += '-withKotlin'
        }
        if (sources) {
            fqn += '-withSources'
        }
        return fqn
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (!(o instanceof IdeaDependency)) return false
        IdeaDependency that = (IdeaDependency) o
        if (extraDependencies != that.extraDependencies) return false
        if (withKotlin != that.withKotlin) return false
        if (buildNumber != that.buildNumber) return false
        if (classes != that.classes) return false
        if (jarFiles != that.jarFiles) return false
        if (sources != that.sources) return false
        if (version != that.version) return false
        if (name != that.name) return false
        return true
    }

    int hashCode() {
        int result
        result = version.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + buildNumber.hashCode()
        result = 31 * result + classes.hashCode()
        result = 31 * result + (sources != null ? sources.hashCode() : 0)
        result = 31 * result + jarFiles.hashCode()
        result = 31 * result + (withKotlin ? 1 : 0)
        result = 31 * result + extraDependencies.hashCode()
        return result
    }
}
