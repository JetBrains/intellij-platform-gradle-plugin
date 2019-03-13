package org.jetbrains.intellij.jbr

class Jbr {
    final String version
    final String javaHome
    final String javaExecutable

    Jbr(String version, File javaHome, String javaExecutable) {
        this.version = version
        this.javaHome = javaHome.path
        this.javaExecutable = javaExecutable
    }
}
