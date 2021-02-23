package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import groovy.transform.CompileStatic
import groovy.transform.ToString
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.Utils

@CompileStatic
@ToString(includeNames = true, includeFields = true, ignoreNulls = true)
class PluginDependencyImpl implements PluginDependency {
    @NotNull
    private String id
    @NotNull
    private String version
    @Nullable
    private String channel

    @Nullable
    private String sinceBuild
    @Nullable
    private String untilBuild

    @Nullable
    private File classesDirectory
    @Nullable
    private File metaInfDirectory
    @Nullable
    private File sourcesDirectory
    @NotNull
    private File artifact
    @NotNull
    private Collection<File> jarFiles = Collections.emptySet()

    private boolean builtin
    private boolean maven

    PluginDependencyImpl(@NotNull String id,
                         @NotNull String version,
                         @NotNull File artifact,
                         boolean builtin = false,
                         boolean maven = false) {
        this.id = id
        this.version = version
        this.artifact = artifact
        this.sourcesDirectory = sourcesDirectory
        this.builtin = builtin
        this.maven = maven
        initFiles()
    }

    private initFiles() {
        if (Utils.isJarFile(artifact)) {
            jarFiles = Collections.singletonList(artifact)
        }
        if (artifact.isDirectory()) {
            File lib = new File(artifact, "lib")
            if (lib.isDirectory()) {
                jarFiles = Utils.collectJars(lib, { file -> true })
            }
            File classes = new File(artifact, "classes")
            if (classes.isDirectory()) {
                classesDirectory = classes
            }
            File metaInf = new File(artifact, "META-INF")
            if (metaInf.isDirectory()) {
                metaInfDirectory = metaInf
            }
        }
    }

    boolean isCompatible(@NotNull IdeVersion ideVersion) {
        return sinceBuild == null ||
                IdeVersion.createIdeVersion(sinceBuild) <= ideVersion &&
                (untilBuild == null || ideVersion <= IdeVersion.createIdeVersion(untilBuild))
    }

    @Override
    PluginDependencyNotation getNotation() {
        return new PluginDependencyNotation(id, version, channel)
    }

    String getId() {
        return id
    }

    void setId(String id) {
        this.id = id
    }

    String getVersion() {
        return version
    }

    void setVersion(String version) {
        this.version = version
    }

    String getChannel() {
        return channel
    }

    void setChannel(String channel) {
        this.channel = channel
    }

    String getSinceBuild() {
        return sinceBuild
    }

    void setSinceBuild(String sinceBuild) {
        this.sinceBuild = sinceBuild
    }

    String getUntilBuild() {
        return untilBuild
    }

    void setUntilBuild(String untilBuild) {
        this.untilBuild = untilBuild
    }

    File getArtifact() {
        return artifact
    }

    void setArtifact(File artifact) {
        this.artifact = artifact
    }

    Collection<File> getJarFiles() {
        return jarFiles
    }

    void setJarFiles(Collection<File> jarFiles) {
        this.jarFiles = jarFiles
    }

    @Override
    @Nullable
    File getClassesDirectory() {
        return classesDirectory
    }

    void setClassesDirectory(@Nullable File classesDirectory) {
        this.classesDirectory = classesDirectory
    }

    @Override
    @Nullable
    File getMetaInfDirectory() {
        return metaInfDirectory
    }

    void setMetaInfDirectory(@Nullable File metaInfDirectory) {
        this.metaInfDirectory = metaInfDirectory
    }

    @Override
    @Nullable
    File getSourcesDirectory() {
        return sourcesDirectory
    }

    void setSourcesDirectory(@Nullable File sourcesDirectory) {
        this.sourcesDirectory = sourcesDirectory
    }

    @Override
    boolean isMaven() {
        return maven
    }

    boolean setMaven(boolean maven) {
        this.maven = maven
    }

    @Override
    boolean isBuiltin() {
        return builtin
    }

    void setBuiltin(boolean builtin) {
        this.builtin = builtin
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (!(o instanceof PluginDependencyImpl)) return false

        PluginDependencyImpl that = (PluginDependencyImpl) o

        if (builtin != that.builtin) return false
        if (artifact != that.artifact) return false
        if (channel != that.channel) return false
        if (classesDirectory != that.classesDirectory) return false
        if (sourcesDirectory != that.sourcesDirectory) return false
        if (id != that.id) return false
        if (jarFiles != that.jarFiles) return false
        if (metaInfDirectory != that.metaInfDirectory) return false
        if (sinceBuild != that.sinceBuild) return false
        if (untilBuild != that.untilBuild) return false
        if (version != that.version) return false

        return true
    }

    int hashCode() {
        int result
        result = id.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + (channel != null ? channel.hashCode() : 0)
        result = 31 * result + (sinceBuild != null ? sinceBuild.hashCode() : 0)
        result = 31 * result + (untilBuild != null ? untilBuild.hashCode() : 0)
        result = 31 * result + (classesDirectory != null ? classesDirectory.hashCode() : 0)
        result = 31 * result + (sourcesDirectory != null ? sourcesDirectory.hashCode() : 0)
        result = 31 * result + (metaInfDirectory != null ? metaInfDirectory.hashCode() : 0)
        result = 31 * result + artifact.hashCode()
        result = 31 * result + jarFiles.hashCode()
        result = 31 * result + (builtin ? 1 : 0)
        return result
    }
}