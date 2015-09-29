package org.jetbrains.intellij

class IntelliJPluginExtension {
    String[] plugins
    String version
    String pluginName
    String sandboxDirectory
    String intellijRepo
    boolean instrumentCode
    boolean updateSinceUntilBuild
    boolean downloadSources
    
    File ideaDirectory
    File ideaSourcesFile
    private final Set<File> intellijFiles = new HashSet<>();
    private final Set<File> runClasspath = new HashSet<>();

    Set<File> getIntellijFiles() {
        return intellijFiles
    }

    Set<File> getRunClasspath() {
        return runClasspath
    }
}
