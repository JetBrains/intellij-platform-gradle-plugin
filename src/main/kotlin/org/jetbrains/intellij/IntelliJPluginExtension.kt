package org.jetbrains.intellij

import java.util.HashSet

open class IntelliJPluginExtension() {
    val plugins = HashSet<String>()
    var version = "142-SNAPSHOT"
    
    fun plugins(vararg plugins : String) {
        this.plugins.addAll(plugins)
    }
}


