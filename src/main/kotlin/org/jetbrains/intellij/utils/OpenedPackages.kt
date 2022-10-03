// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.utils

import org.gradle.internal.os.OperatingSystem

// OpenedPackages list synchronized with:
// https://raw.githubusercontent.com/JetBrains/intellij-community/master/plugins/devkit/devkit-core/src/run/OpenedPackages.txt
// last version: bc3f330f28b552bcc1fb3fd98212aee68e7e3280
internal val OpenedPackages = listOf(
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens=java.base/java.net=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-opens=java.base/java.nio.charset=ALL-UNNAMED",
    "--add-opens=java.base/java.text=ALL-UNNAMED",
    "--add-opens=java.base/java.time=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED",
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED",
    "--add-opens=java.base/sun.security.util=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.image=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
    "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
    "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
    "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.swing=ALL-UNNAMED",
    "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED",
    "--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED",
) + with(OperatingSystem.current()) {
    when {
        isLinux -> listOf(
            "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
            "--add-opens=java.desktop/com.sun.java.swing.plaf.gtk=ALL-UNNAMED",
        )

        isMacOsX -> listOf(
            "--add-opens=java.desktop/com.apple.laf=ALL-UNNAMED",
            "--add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED",
            "--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
        )

        isWindows -> listOf(
            "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
        )

        else -> listOf()
    }
}
