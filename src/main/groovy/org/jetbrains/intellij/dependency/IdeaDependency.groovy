package org.jetbrains.intellij.dependency

import com.google.common.base.Predicate
import com.intellij.structure.impl.utils.JarsUtils
import groovy.transform.ToString
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

@ToString(includeNames = true, includeFields = true, ignoreNulls = true)
 class IdeaDependency implements Serializable {
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

    IdeaDependency(@NotNull String version, @NotNull String buildNumber, @NotNull File classes, @Nullable File sources, 
                   boolean withKotlin) {
        this.version = version
        this.buildNumber = buildNumber
        this.classes = classes
        this.sources = sources
        this.withKotlin = withKotlin
        jarFiles = collectJarFiles()
    }

    private def collectJarFiles() {
        if (classes.isDirectory()) {
            File lib = new File(classes, "lib")
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

    String getFqn() {
        def fqn = "idea$version"
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
        if (withKotlin != that.withKotlin) return false
        if (buildNumber != that.buildNumber) return false
        if (classes != that.classes) return false
        if (jarFiles != that.jarFiles) return false
        if (sources != that.sources) return false
        if (version != that.version) return false
        return true
    }

    int hashCode() {
        int result
        result = version.hashCode()
        result = 31 * result + buildNumber.hashCode()
        result = 31 * result + classes.hashCode()
        result = 31 * result + (sources != null ? sources.hashCode() : 0)
        result = 31 * result + jarFiles.hashCode()
        result = 31 * result + (withKotlin ? 1 : 0)
        return result
    }
}
