package org.jetbrains.intellij.jbre

class Jbre {
    final String version
    final String javaHome
    final String javaExecutable

    Jbre(String version, File javaHome, String javaExecutable) {
        this.version = version
        this.javaHome = javaHome.path
        this.javaExecutable = javaExecutable
    }
}
