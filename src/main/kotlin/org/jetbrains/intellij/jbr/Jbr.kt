package org.jetbrains.intellij.jbr

import java.io.File

data class Jbr(
    val version: String,
    val javaHome: File,
    val javaExecutable: String,
)
