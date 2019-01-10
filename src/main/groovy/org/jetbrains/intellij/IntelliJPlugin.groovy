package org.jetbrains.intellij

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.PluginInstantiationException
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.tooling.BuildException
import org.gradle.util.VersionNumber
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.dependency.IdeaDependencyManager
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginDependencyManager
import org.jetbrains.intellij.dependency.PluginProjectDependency
import org.jetbrains.intellij.jbre.JbreResolver
import org.jetbrains.intellij.tasks.*

class IntelliJPlugin implements Plugin<Project> {
    public static final GROUP_NAME = "intellij"
    public static final EXTENSION_NAME = "intellij"
    public static final String DEFAULT_SANDBOX = 'idea-sandbox'
    public static final String PATCH_PLUGIN_XML_TASK_NAME = "patchPluginXml"
    public static final String PLUGIN_XML_DIR_NAME = "patchedPluginXmlFiles"
    public static final String PREPARE_SANDBOX_TASK_NAME = "prepareSandbox"
    public static final String PREPARE_TESTING_SANDBOX_TASK_NAME = "prepareTestingSandbox"
    public static final String VERIFY_PLUGIN_TASK_NAME = "verifyPlugin"
    public static final String RUN_IDE_TASK_NAME = "runIde"
    public static final String BUILD_SEARCHABLE_OPTIONS_TASK_NAME = "buildSearchableOptions"
    public static final String SEARCHABLE_OPTIONS_DIR_NAME = "searchableOptions"
    public static final String JAR_SEARCHABLE_OPTIONS_TASK_NAME = "jarSearchableOptions"
    public static final String BUILD_PLUGIN_TASK_NAME = "buildPlugin"
    public static final String PUBLISH_PLUGIN_TASK_NAME = "publishPlugin"

    public static final String IDEA_CONFIGURATION_NAME = "idea"
    public static final String IDEA_PLUGINS_CONFIGURATION_NAME = "ideaPlugins"

    public static final LOG = Logging.getLogger(IntelliJPlugin)
    public static final String DEFAULT_IDEA_VERSION = "LATEST-EAP-SNAPSHOT"
    public static final String DEFAULT_INTELLIJ_REPO = 'https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository'
    public static final String DEFAULT_JBRE_REPO = 'https://cache-redirector.jetbrains.com/jetbrains.bintray.com/intellij-jdk'
    public static final String DEFAULT_INTELLIJ_PLUGINS_REPO = 'https://cache-redirector.jetbrains.com/plugins.jetbrains.com/maven'

    @Override
    void apply(Project project) {
        checkGradleVersion(project)
        project.getPlugins().apply(JavaPlugin)
        def intellijExtension = project.extensions.create(EXTENSION_NAME, IntelliJPluginExtension) as IntelliJPluginExtension
        intellijExtension.with {
            pluginName = project.name
            sandboxDirectory = new File(project.buildDir, DEFAULT_SANDBOX).absolutePath
            downloadSources = !System.getenv().containsKey('CI')
        }
        configureConfigurations(project)
        configureTasks(project, intellijExtension)
    }

    private static void checkGradleVersion(@NotNull Project project) {
        def gradleVersionComponents = project.gradle.gradleVersion.split('\\.')
        if (gradleVersionComponents.length > 1 &&
                Integer.parseInt(gradleVersionComponents[0]) < 3 ||
                Integer.parseInt(gradleVersionComponents[0]) == 3 && Integer.parseInt(gradleVersionComponents[1]) < 4) {
            throw new PluginInstantiationException("gradle-intellij-plugin requires Gradle 3.4 and higher")
        }
    }

    private static void configureConfigurations(@NotNull Project project) {
        def idea = project.configurations.create(IDEA_CONFIGURATION_NAME)
        def ideaPlugins = project.configurations.create(IDEA_PLUGINS_CONFIGURATION_NAME)
        project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom idea, ideaPlugins
        project.configurations.getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME).extendsFrom idea, ideaPlugins
    }

    private static def configureTasks(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring IntelliJ IDEA gradle plugin")
        configurePatchPluginXmlTask(project, extension)
        configurePrepareSandboxTasks(project, extension)
        configurePluginVerificationTask(project)
        configureRunIdeaTask(project, extension)
        configureBuildSearchableOptionsTask(project, extension)
        configureJarSearchableOptionsTask(project)
        configureBuildPluginTask(project)
        configurePublishPluginTask(project)
        configureProcessResources(project)
        configureInstrumentation(project, extension)
        configureDependencyExtensions(project, extension)
        assert !project.state.executed : "afterEvaluate is a no-op for an executed project"
        project.afterEvaluate { Project p -> configureProjectAfterEvaluate(p, extension) }
    }

    private static void configureProjectAfterEvaluate(@NotNull Project project,
                                                      @NotNull IntelliJPluginExtension extension) {
        for (def subproject : project.subprojects) {
            if (subproject.plugins.findPlugin(IntelliJPlugin) != null) {
                continue
            }
            def subprojectExtension = subproject.extensions.findByType(IntelliJPluginExtension)
            if (subprojectExtension) {
                configureProjectAfterEvaluate(subproject, subprojectExtension)
            }
        }

        configureIntellijDependency(project, extension)
        configurePluginDependencies(project, extension)
        configureTestTasks(project, extension)
    }

    private static void configureDependencyExtensions(@NotNull Project project,
                                                      @NotNull IntelliJPluginExtension extension) {
        project.dependencies.ext.intellij = { Closure filter = {} ->
            def dependency = extension.ideaDependency
            if (dependency == null || dependency.jarFiles == null || dependency.jarFiles.empty) {
                throw new GradleException('intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block')
            }
            project.files(dependency.jarFiles).asFileTree.matching(filter)
        }

        project.dependencies.ext.intellijPlugin = { String plugin, Closure filter = {} ->
            def pluginDep = extension.pluginDependencies.find { it.id == plugin }
            if (pluginDep == null || pluginDep.jarFiles == null || pluginDep.jarFiles.empty) {
                throw new GradleException("intellij plugin '$plugin' is not (yet) configured or not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies to them in the afterEvaluate block")
            }
            project.files(pluginDep.jarFiles).asFileTree.matching(filter)
        }

        project.dependencies.ext.intellijPlugins = { String... plugins ->
            def selectedPlugins = new HashSet<PluginDependency>()
            def invalidPlugins = []
            for (pluginName in plugins) {
                def plugin = extension.pluginDependencies.find { it.id == pluginName }
                if (plugin == null || plugin.jarFiles == null || plugin.jarFiles.empty) {
                    invalidPlugins.add(pluginName)
                }
                else {
                    selectedPlugins.add(plugin)
                }
            }
            if (!invalidPlugins.empty) {
                throw new GradleException("intellij plugins $invalidPlugins are not (yet) configured or not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies to them in the afterEvaluate block")
            }
            project.files(selectedPlugins.collect { it.jarFiles })
        }

        project.dependencies.ext.intellijExtra = { String extra, Closure filter = {} ->
            def dependency = extension.ideaDependency
            def extraDep = dependency != null ? dependency.extraDependencies.find { it.name == extra } : null
            if (extraDep == null || extraDep.jarFiles == null || extraDep.jarFiles.empty) {
                throw new GradleException("intellij extra artefact '$extra' is not (yet) configured or not found. Please note that you should specify extra dependencies in the intellij.extraDependencies property and configure dependencies to them in the afterEvaluate block")
            }
            project.files(extraDep.jarFiles).asFileTree.matching(filter)
        }
    }

    private static void configureIntellijDependency(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring IntelliJ IDEA dependency")
        def resolver = new IdeaDependencyManager(extension.intellijRepo ?: DEFAULT_INTELLIJ_REPO)
        def ideaDependency
        if (extension.localPath != null) {
            if (extension.version != null) {
                LOG.warn("Both `localPath` and `version` specified, second would be ignored")
            }
            LOG.info("Using path to locally installed IDE: '${extension.localPath}'")
            ideaDependency = resolver.resolveLocal(project, extension.localPath, extension.localSourcesPath)
        } else {
            LOG.info("Using IDE from remote repository")
            def version = extension.version ?: DEFAULT_IDEA_VERSION
            ideaDependency = resolver.resolveRemote(project, version, extension.type, extension.downloadSources,
                    extension.extraDependencies)
        }
        extension.ideaDependency = ideaDependency
        if (extension.configureDefaultDependencies) {
            LOG.info("IntelliJ IDEA ${ideaDependency.buildNumber} is used for building")
            resolver.register(project, ideaDependency, IDEA_CONFIGURATION_NAME)
            if (!ideaDependency.extraDependencies.empty) {
                LOG.info("Note: IntelliJ IDEA ${ideaDependency.buildNumber} extra dependencies (${ideaDependency.extraDependencies}) should be applied manually")
            }

            def toolsJar = Jvm.current().toolsJar
            if (toolsJar) {
                project.dependencies.add(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, project.files(toolsJar))
            }
        }
        else {
            LOG.info("IntelliJ IDEA ${ideaDependency.buildNumber} dependencies are applied manually")
        }
    }

    private static void configurePluginDependencies(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring IntelliJ IDEA plugin dependencies")
        def ideVersion = IdeVersion.createIdeVersion(extension.ideaDependency.buildNumber)
        def resolver = new PluginDependencyManager(project.gradle.gradleUserHomeDir.absolutePath, extension.ideaDependency)
        project.repositories.maven { it.url = extension.pluginsRepo }
        extension.plugins.each {
            LOG.info("Configuring IntelliJ plugin $it")
            if (it instanceof Project) {
                project.dependencies.add(IDEA_PLUGINS_CONFIGURATION_NAME, it)
                if (it.state.executed) {
                    configureProjectPluginDependency(project, it, extension)
                } else {
                    it.afterEvaluate {
                        configureProjectPluginDependency(project, it, extension)
                    }
                }
            } else {
                def (pluginId, pluginVersion, channel) = Utils.parsePluginDependencyString(it.toString())
                if (!pluginId) {
                    throw new BuildException("Failed to resolve plugin $it", null)
                }
                def plugin = resolver.resolve(project, pluginId, pluginVersion, channel)
                if (plugin == null) {
                    throw new BuildException("Failed to resolve plugin $it", null)
                }
                if (ideVersion != null && !plugin.isCompatible(ideVersion)) {
                    throw new BuildException("Plugin $it is not compatible to ${ideVersion.asString()}", null)
                }
                if (extension.configureDefaultDependencies) {
                    resolver.register(project, plugin, plugin.builtin ? IDEA_CONFIGURATION_NAME : IDEA_PLUGINS_CONFIGURATION_NAME)
                }
                extension.pluginDependencies.add(plugin)
                project.tasks.withType(PrepareSandboxTask).each {
                    it.configureExternalPlugin(plugin)
                }
            }
        }
    }

    private static void configureProjectPluginDependency(@NotNull Project project,
                                                         @NotNull Project dependency,
                                                         @NotNull IntelliJPluginExtension extension) {
        if (dependency.plugins.findPlugin(IntelliJPlugin) == null) {
            throw new BuildException("Cannot use $dependency as a plugin dependency. IntelliJ Plugin is not found." + dependency.plugins, null)
        }
        def pluginDependency = new PluginProjectDependency(dependency)
        extension.pluginDependencies.add(pluginDependency)
        def dependencySandboxTask = dependency.tasks.findByName(PREPARE_SANDBOX_TASK_NAME)
        project.tasks.withType(PrepareSandboxTask).each {
            it.dependsOn(dependencySandboxTask)
            it.configureCompositePlugin(pluginDependency)
        }
    }

    private static void configurePatchPluginXmlTask(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring patch plugin.xml task")
        project.tasks.create(PATCH_PLUGIN_XML_TASK_NAME, PatchPluginXmlTask).with {
            group = GROUP_NAME
            description = "Patch plugin xml files with corresponding since/until build numbers and version attributes"
            conventionMapping('version', { project.version?.toString() })
            conventionMapping('pluginXmlFiles', { Utils.sourcePluginXmlFiles(project) })
            conventionMapping('destinationDir', { new File(project.buildDir, PLUGIN_XML_DIR_NAME) })
            conventionMapping('sinceBuild', {
                if (extension.updateSinceUntilBuild) {
                    def ideVersion = IdeVersion.createIdeVersion(extension.ideaDependency.buildNumber)
                    return "$ideVersion.baselineVersion.$ideVersion.build".toString()
                }
            })
            conventionMapping('untilBuild', {
                if (extension.updateSinceUntilBuild) {
                    def ideVersion = IdeVersion.createIdeVersion(extension.ideaDependency.buildNumber)
                    return extension.sameSinceUntilBuild ? "${getSinceBuild()}.*".toString()
                            : "$ideVersion.baselineVersion.*".toString()
                }
            })
        }
    }

    private static void configurePrepareSandboxTasks(@NotNull Project project,
                                                     @NotNull IntelliJPluginExtension extension) {
        configurePrepareSandboxTask(project, extension, false)
        configurePrepareSandboxTask(project, extension, true)
    }

    private static void configurePrepareSandboxTask(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension,
                                                    boolean inTest) {
        LOG.info("Configuring prepare IntelliJ sandbox task")
        def taskName = inTest ? PREPARE_TESTING_SANDBOX_TASK_NAME : PREPARE_SANDBOX_TASK_NAME
        project.tasks.create(taskName, PrepareSandboxTask).with {
            group = GROUP_NAME
            description = "Prepare sandbox directory with installed plugin and its dependencies."
            conventionMapping('pluginName', { extension.pluginName })
            conventionMapping('pluginJar', { (project.tasks.findByName(JavaPlugin.JAR_TASK_NAME) as Jar).archivePath })
            conventionMapping('destinationDir', { project.file(Utils.pluginsDir(extension.sandboxDirectory, inTest)) })
            conventionMapping('configDirectory', { project.file(Utils.configDir(extension.sandboxDirectory, inTest)) })
            conventionMapping('librariesToIgnore', { project.files(extension.ideaDependency.jarFiles) })
            conventionMapping('pluginDependencies', { extension.pluginDependencies })
            dependsOn(JavaPlugin.JAR_TASK_NAME)
        }
    }

    private static void configurePluginVerificationTask(@NotNull Project project) {
        LOG.info("Configuring plugin verification task")
        project.tasks.create(VERIFY_PLUGIN_TASK_NAME, VerifyPluginTask).with {
            group = GROUP_NAME
            description = "Verifies built plugin."
            conventionMapping('pluginDirectory', {
                def prepareSandboxTask = project.tasks.findByName(PREPARE_SANDBOX_TASK_NAME) as PrepareSandboxTask
                new File(prepareSandboxTask.getDestinationDir(), prepareSandboxTask.getPluginName())
            })
            dependsOn { project.getTasksByName(PREPARE_SANDBOX_TASK_NAME, false) }
        }
    }

    private static void configureRunIdeaTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring run IntelliJ task")
        project.tasks.create(RUN_IDE_TASK_NAME, RunIdeTask).with { RunIdeTask task ->
            task.group = GROUP_NAME
            task.description = "Runs Intellij IDEA with installed plugin."
            prepareConventionMappingsForRunIdeTask(project, extension, task)
            task.dependsOn(PREPARE_SANDBOX_TASK_NAME)
        }
    }

    private static void configureBuildSearchableOptionsTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring build searchable options task")
        project.tasks.create(BUILD_SEARCHABLE_OPTIONS_TASK_NAME, RunIdeTask).with { RunIdeTask task ->
            task.group = GROUP_NAME
            task.description = "Builds searchable options for plugin."
            prepareConventionMappingsForRunIdeTask(project, extension, task)
            task.args(["traverseUI", "$project.buildDir/$SEARCHABLE_OPTIONS_DIR_NAME", "true"])
            task.dependsOn(PREPARE_SANDBOX_TASK_NAME)
            task.onlyIf {
                def number = Utils.ideaBuildNumber(Utils.ideaSdkDirectory(extension))
                VersionNumber.parse(number[number.indexOf('-') + 1..-1]) >= VersionNumber.parse("191.2752")
            }
        }
    }

    private static void prepareConventionMappingsForRunIdeTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension, 
                                                               @NotNull RunIdeTask task) {
        def prepareSandboxTask = project.tasks.findByName(PREPARE_SANDBOX_TASK_NAME) as PrepareSandboxTask
        task.conventionMapping.map("ideaDirectory", { Utils.ideaSdkDirectory(extension) })
        task.conventionMapping.map("requiredPluginIds", { Utils.getPluginIds(project) })
        task.conventionMapping.map("configDirectory", { prepareSandboxTask.getConfigDirectory() })
        task.conventionMapping.map("pluginsDirectory", { prepareSandboxTask.getDestinationDir() })
        task.conventionMapping.map("systemDirectory", {
            project.file(Utils.systemDir(extension.sandboxDirectory, false))
        })
        task.conventionMapping("executable", {
            def jbreResolver = new JbreResolver(project)
            def jbreVersion = task.getJbreVersion()
            if (jbreVersion != null) {
                def jbre = jbreResolver.resolve(jbreVersion)
                if (jbre != null) {
                    return jbre.javaExecutable
                }
                LOG.warn("Cannot resolve JBRE $jbreVersion. Falling back to builtin JBRE.")
            }
            def builtinJbreVersion = Utils.getBuiltinJbreVersion(Utils.ideaSdkDirectory(extension))
            if (builtinJbreVersion != null) {
                def builtinJbre = jbreResolver.resolve(builtinJbreVersion)
                if (builtinJbre != null) {
                    return builtinJbre.javaExecutable
                }
                LOG.warn("Cannot resolve builtin JBRE $builtinJbreVersion. Falling local Java.")
            }
            return Jvm.current().javaExecutable.absolutePath
        })
    }

    private static void configureJarSearchableOptionsTask(@NotNull Project project) {
        LOG.info("Configuring jar searchable options task")
        project.tasks.create(JAR_SEARCHABLE_OPTIONS_TASK_NAME, JarSearchableOptionsTask).with {
            group = GROUP_NAME
            description = "Jars searchable options."
            conventionMapping.map('baseName', { "lib/searchableOptions" })
            conventionMapping.map('destinationDir', { new File(project.buildDir, "libsSearchableOptions") })
            dependsOn(BUILD_SEARCHABLE_OPTIONS_TASK_NAME)
            onlyIf { new File(project.buildDir, SEARCHABLE_OPTIONS_DIR_NAME).isDirectory() }
        }
    }

    private static void configureInstrumentation(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring IntelliJ compile tasks")
        project.sourceSets.all { SourceSet sourceSet ->
            def instrumentTask = project.tasks.create(sourceSet.getTaskName('instrument', 'code'), IntelliJInstrumentCodeTask)
            instrumentTask.sourceSet = sourceSet
            instrumentTask.with {
                dependsOn sourceSet.classesTaskName
                onlyIf { extension.instrumentCode }
                conventionMapping('compilerVersion', {
                    def ideVersion = IdeVersion.createIdeVersion(ideaDependency.buildNumber)
                    return "$ideVersion.baselineVersion.$ideVersion.build".toString()
                })
                conventionMapping('ideaDependency', { extension.ideaDependency })
                conventionMapping('javac2', {
                    def javac2 = project.file("$extension.ideaDependency.classes/lib/javac2.jar")
                    if (javac2?.exists()) {
                        return javac2
                    }
                })
                conventionMapping('outputDir', {
                    def output = sourceSet.output
                    // @since Gradle 4.0
                    def classesDir = output.hasProperty('classesDirs') ? output.classesDirs.first() : output.classesDir
                    new File(classesDir.parentFile, "${sourceSet.name}-instrumented")
                })
            }

            def updateTask = project.tasks.create('post' + instrumentTask.name.capitalize())
            updateTask.with {
                dependsOn instrumentTask
                onlyIf { extension.instrumentCode }

                doLast {
                    def sourceSetOutput = sourceSet.output

                    // @since Gradle 4.0
                    def oldClassesDir = sourceSetOutput.hasProperty("classesDirs") ?
                            sourceSetOutput.classesDirs :
                            sourceSetOutput.classesDir

                    // Set the classes dir to the new one with the instrumented classes

                    // @since Gradle 4.0
                    if (sourceSetOutput.hasProperty("classesDirs"))
                        sourceSetOutput.classesDirs.from = instrumentTask.outputDir else
                        sourceSetOutput.classesDir = instrumentTask.outputDir

                    if (sourceSet.name == SourceSet.MAIN_SOURCE_SET_NAME) {
                        // When we change the output classes directory, Gradle will automatically configure
                        // the test compile tasks to use the instrumented classes. Normally this is fine,
                        // however, it causes problems for Kotlin projects:

                        // The "internal" modifier can be used to restrict access to the same module.
                        // To make it possible to use internal methods from the main source set in test classes,
                        // the Kotlin Gradle plugin adds the original output directory of the Java task
                        // as "friendly directory" which makes it possible to access internal members
                        // of the main module.

                        // This fails when we change the classes dir. The easiest fix is to prepend the
                        // classes from the "friendly directory" to the compile classpath.
                        AbstractCompile testCompile = project.tasks.findByName('compileTestKotlin') as AbstractCompile
                        if (testCompile) {
                            testCompile.classpath = project.files(oldClassesDir, testCompile.classpath)
                        }
                    }
                }
            }

            // Ensure that our task is invoked when the source set is built
            sourceSet.compiledBy(updateTask)
        }
    }

    private static void configureTestTasks(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring IntelliJ tests tasks")
        project.tasks.withType(Test).each {
            def configDirectory = project.file(Utils.configDir(extension.sandboxDirectory, true))
            def systemDirectory = project.file(Utils.systemDir(extension.sandboxDirectory, true))
            def pluginsDirectory = project.file(Utils.pluginsDir(extension.sandboxDirectory, true))

            it.enableAssertions = true
            it.systemProperties(Utils.getIdeaSystemProperties(configDirectory, systemDirectory, pluginsDirectory, Utils.getPluginIds(project)))
            it.jvmArgs = Utils.getIdeaJvmArgs(it, it.jvmArgs, Utils.ideaSdkDirectory(extension))
            it.classpath += project.files("$extension.ideaDependency.classes/lib/resources.jar",
                    "$extension.ideaDependency.classes/lib/idea.jar")
            it.outputs.dir(systemDirectory)
            it.outputs.dir(configDirectory)
            it.dependsOn(project.getTasksByName(PREPARE_TESTING_SANDBOX_TASK_NAME, false))
        }
    }

    private static void configureBuildPluginTask(@NotNull Project project) {
        LOG.info("Configuring building IntelliJ IDEA plugin task")
        def prepareSandboxTask = project.tasks.findByName(PREPARE_SANDBOX_TASK_NAME) as PrepareSandboxTask
        def jarSearchableOptionsTask = project.tasks.findByName(JAR_SEARCHABLE_OPTIONS_TASK_NAME) as Jar
        Zip zip = project.tasks.create(BUILD_PLUGIN_TASK_NAME, Zip).with {
            description = "Bundles the project as a distribution."
            group = GROUP_NAME
            from([
              "${prepareSandboxTask.getDestinationDir()}/${prepareSandboxTask.getPluginName()}",
              jarSearchableOptionsTask.getDestinationDir()
            ])
            into { prepareSandboxTask.getPluginName() }
            dependsOn(JAR_SEARCHABLE_OPTIONS_TASK_NAME)
            conventionMapping.map('baseName', { prepareSandboxTask.getPluginName() })
            it
        }
        Configuration archivesConfiguration = project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION)
        if (archivesConfiguration) {
            ArchivePublishArtifact zipArtifact = new ArchivePublishArtifact(zip)
            archivesConfiguration.getArtifacts().add(zipArtifact)
            project.getExtensions().getByType(DefaultArtifactPublicationSet.class).addCandidate(zipArtifact)
            project.getComponents().add(new IntelliJPluginLibrary())
        }
    }

    private static void configurePublishPluginTask(@NotNull Project project) {
        LOG.info("Configuring publishing IntelliJ IDEA plugin task")
        project.tasks.create(PUBLISH_PLUGIN_TASK_NAME, PublishTask).with {
            group = GROUP_NAME
            description = "Publish plugin distribution on plugins.jetbrains.com."
            conventionMapping('distributionFile', {
                def buildPluginTask = project.tasks.findByName(BUILD_PLUGIN_TASK_NAME) as Zip
                def distributionFile = buildPluginTask?.archivePath
                return distributionFile?.exists() ? distributionFile : null
            })
            dependsOn { project.getTasksByName(BUILD_PLUGIN_TASK_NAME, false) }
            dependsOn { project.getTasksByName(VERIFY_PLUGIN_TASK_NAME, false) }
        }
    }

    private static void configureProcessResources(@NotNull Project project) {
        LOG.info("Configuring IntelliJ resources task")
        def processResourcesTask = project.tasks.findByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME) as ProcessResources
        if (processResourcesTask) {
            processResourcesTask.from(project.tasks.findByName(PATCH_PLUGIN_XML_TASK_NAME)) {
                into("META-INF")
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
        }
    }
}
