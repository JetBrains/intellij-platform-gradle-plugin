package org.jetbrains.intellij.dependency

import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.tasks.TaskDependency

class IntellijIvyArtifact implements IvyArtifact {
    private final DefaultTaskDependency buildDependencies = new DefaultTaskDependency()
    private final File file
    private String name
    private String extension
    private String type
    private String classifier
    private String conf

    IntellijIvyArtifact(File file, String name, String extension, String type, String classifier) {
        this.file = file
        this.name = name
        this.extension = extension
        this.type = type
        this.classifier = classifier ?: null
    }

    File getFile() {
        return file
    }

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    String getType() {
        return type
    }

    void setType(String type) {
        this.type = type
    }

    String getExtension() {
        return extension
    }

    void setExtension(String extension) {
        this.extension = extension
    }

    String getClassifier() {
        return classifier
    }

    void setClassifier(String classifier) {
        this.classifier = classifier
    }

    String getConf() {
        return conf
    }

    void setConf(String conf) {
        this.conf = conf
    }

    void builtBy(Object... tasks) {
        buildDependencies.add(tasks)
    }

    TaskDependency getBuildDependencies() {
        return buildDependencies
    }

    @Override
    String toString() {
        return String.format("%s %s:%s:%s:%s", getClass().getSimpleName(), getName(), getType(), getExtension(), getClassifier())
    }
}