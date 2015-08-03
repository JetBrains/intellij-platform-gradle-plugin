package org.jetbrains.intellij

class IntelliJPluginExtension {
    String[] plugins
    String version
    String pluginName
    String sandboxDirectory
    
    File ideaDirectory
    File ideaSourcesFile
    private final Set<File> intellijFiles = new HashSet<>();

    Set<File> getIntellijFiles() {
        return intellijFiles
    }
}
