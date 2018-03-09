package org.jetbrains.intellij.jbre

class Jbre {
    final String version
    final String javaHome
    final String javaExecutable

    Jbre(String version, String javaHome, String javaExecutable) {
        this.version = version
        this.javaHome = javaHome
        this.javaExecutable = javaExecutable
    }
}
