package org.jetbrains.intellij.dependency

interface PluginsRepositoryConfiguration {

    /**
     * Use default marketplace repository
     */
    fun marketplace()

    /**
     * Use a Maven repository with plugin artifacts
     */
    fun maven(url: String)

    /**
     * Use custom plugin repository. The URL should point to the `plugins.xml` or `updatePlugins.xml` file.
     */
    fun custom(url: String)

    fun getRepositories(): List<PluginsRepository>
}
