package org.jetbrains.intellij

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifact
import org.gradle.api.tasks.SourceSet
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable


class Utils {
    @NotNull
    public static SourceSet mainSourceSet(@NotNull Project project) {
        JavaPluginConvention javaConvention = project.convention.getPlugin(JavaPluginConvention);
        return javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    }

    @Nullable
    public static File javaHomeLib() {
        def javaHome = System.getProperty("java.home")
        return javaHome != null && !javaHome.isEmpty() ? new File("${javaHome}", "/../lib") : null;
    }

    @NotNull
    public static DefaultIvyArtifact createDependency(File file, String configuration, File baseDir) {
        def relativePath = baseDir.toURI().relativize(file.toURI()).getPath()
        def artifact = new DefaultIvyArtifact(file, relativePath - ".jar", "jar", "jar", null)
        artifact.conf = configuration
        artifact
    }
}
