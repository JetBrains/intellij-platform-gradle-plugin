package org.jetbrains.intellij

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.PluginInstantiationException
import org.gradle.tooling.BuildException
import org.gradle.util.VersionNumber
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginProjectDependency
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.RunIdeBase
import org.jetbrains.intellij.tasks.RunIdeForUiTestTask

class IntelliJPluginGr extends IntelliJPlugin {

    @Override
    void apply(Project project) {
        checkGradleVersion(project)
        project.getPlugins().apply(JavaPlugin)
        def intellijExtension = project.extensions.create(IntelliJPluginConstants.EXTENSION_NAME, IntelliJPluginExtension, project.objects) as IntelliJPluginExtension
        intellijExtension.with {
            pluginName.convention(project.provider {
                project.name
            })
            updateSinceUntilBuild.convention(true)
            sameSinceUntilBuild.convention(false)
            instrumentCode.convention(true)
            sandboxDirectory.convention(project.provider({
                new File(project.buildDir, IntelliJPluginConstants.DEFAULT_SANDBOX).absolutePath
            }))
            intellijRepo.convention(IntelliJPluginConstants.DEFAULT_INTELLIJ_REPO)
            downloadSources.convention(!System.getenv().containsKey("CI"))
            configureDefaultDependencies.convention(true)
            type.convention("IC")
        }
        configureConfigurations(project, intellijExtension)
        configureTasks(project, intellijExtension)
    }

    private void checkGradleVersion(@NotNull Project project) {
        if (VersionNumber.parse(project.gradle.gradleVersion) < VersionNumber.parse("5.1")) {
            throw new PluginInstantiationException("gradle-intellij-plugin requires Gradle 5.1 and higher")
        }
    }

    private void configureConfigurations(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        def idea = project.configurations.create(IntelliJPluginConstants.IDEA_CONFIGURATION_NAME).setVisible(false)
        configureIntellijDependency(project, extension, idea)

        def ideaPlugins = project.configurations.create(IntelliJPluginConstants.IDEA_PLUGINS_CONFIGURATION_NAME).setVisible(false)
        configurePluginDependencies(project, extension, ideaPlugins)

        def defaultDependencies = project.configurations.create("intellijDefaultDependencies").setVisible(false)
        defaultDependencies.defaultDependencies { dependencies ->
            dependencies.add(project.dependencies.create('org.jetbrains:annotations:19.0.0'))
        }

        project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom defaultDependencies, idea, ideaPlugins
        project.configurations.getByName(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME).extendsFrom defaultDependencies, idea, ideaPlugins
    }

    private def configureTasks(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        Utils.info(project, "Configuring plugin")
        project.tasks.whenTaskAdded {
            if (it instanceof RunIdeBase) {
                prepareConventionMappingsForRunIdeTask(project, extension, it, IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
            }
            if (it instanceof RunIdeForUiTestTask) {
                prepareConventionMappingsForRunIdeTask(project, extension, it, IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME)
            }
        }
        configurePatchPluginXmlTask(project, extension)
        configureRobotServerDownloadTask(project)
        configurePrepareSandboxTasks(project, extension)
        configureRunPluginVerifierTask(project)
        configurePluginVerificationTask(project)
        configureRunIdeaTask(project)
        configureRunIdeaForUiTestsTask(project)
        configureBuildSearchableOptionsTask(project, extension)
        configureJarSearchableOptionsTask(project)
        configureBuildPluginTask(project)
        configurePublishPluginTask(project)
        configureProcessResources(project)
        configureInstrumentation(project, extension)
        configureDependencyExtensions(project, extension)
        assert !project.state.executed: "afterEvaluate is a no-op for an executed project"
        project.afterEvaluate { Project p -> configureProjectAfterEvaluate(p, extension) }
    }

    private void configureProjectAfterEvaluate(@NotNull Project project,
                                               @NotNull IntelliJPluginExtension extension) {
        for (def subproject : project.subprojects) {
            if (subproject.plugins.findPlugin(IntelliJPluginGr) != null) {
                continue
            }
            def subprojectExtension = subproject.extensions.findByType(IntelliJPluginExtension)
            if (subprojectExtension) {
                configureProjectAfterEvaluate(subproject, subprojectExtension)
            }
        }

        configureTestTasks(project, extension)
    }

    private void configureDependencyExtensions(@NotNull Project project,
                                               @NotNull IntelliJPluginExtension extension) {
        project.dependencies.ext.intellij = { Closure filter = {} ->
            if (!project.state.executed) {
                throw new GradleException('intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block')
            }
            project.files(extension.getIdeaDependency(project).jarFiles).asFileTree.matching(filter)
        }

        project.dependencies.ext.intellijPlugin = { String plugin, Closure filter = {} ->
            if (!project.state.executed) {
                throw new GradleException("intellij plugin '$plugin' is not (yet) configured. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
            }
            def pluginDep = extension.getPluginDependenciesList(project).find { it.id == plugin }
            if (pluginDep == null || pluginDep.jarFiles == null || pluginDep.jarFiles.empty) {
                throw new GradleException("intellij plugin '$plugin' is not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
            }
            project.files(pluginDep.jarFiles).asFileTree.matching(filter)
        }

        project.dependencies.ext.intellijPlugins = { String... plugins ->
            def selectedPlugins = new HashSet<PluginDependency>()
            def nonValidPlugins = []
            for (pluginName in plugins) {
                def plugin = extension.getPluginDependenciesList(project).find { it.id == pluginName }
                if (plugin == null || plugin.jarFiles == null || plugin.jarFiles.empty) {
                    nonValidPlugins.add(pluginName)
                } else {
                    selectedPlugins.add(plugin)
                }
            }
            if (!nonValidPlugins.empty) {
                throw new GradleException("intellij plugins $nonValidPlugins are not (yet) configured or not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
            }
            project.files(selectedPlugins.collect { it.jarFiles })
        }

        project.dependencies.ext.intellijExtra = { String extra, Closure filter = {} ->
            if (!project.state.executed) {
                throw new GradleException('intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block')
            }
            def dependency = extension.getIdeaDependency(project)
            def extraDep = dependency != null ? dependency.extraDependencies.find { it.name == extra } : null
            if (extraDep == null || extraDep.jarFiles == null || extraDep.jarFiles.empty) {
                throw new GradleException("intellij extra artifact '$extra' is not found. Please note that you should specify extra dependencies in the intellij.extraDependencies property and configure dependencies on them in the afterEvaluate block")
            }
            project.files(extraDep.jarFiles).asFileTree.matching(filter)
        }
    }

    void configureProjectPluginTasksDependency(@NotNull Project project, @NotNull Project dependency) {
        // invoke before tasks graph is ready
        if (dependency.plugins.findPlugin(IntelliJPluginGr) == null) {
            throw new BuildException("Cannot use $dependency as a plugin dependency. IntelliJ Plugin is not found." + dependency.plugins, null)
        }
        def dependencySandboxTask = dependency.tasks.findByName(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        project.tasks.withType(PrepareSandboxTask).each {
            it.dependsOn(dependencySandboxTask)
        }
    }

    void configureProjectPluginDependency(@NotNull Project project,
                                          @NotNull Project dependency,
                                          @NotNull DependencySet dependencies,
                                          @NotNull IntelliJPluginExtension extension) {
        // invoke on demand, when plugins artifacts are needed
        if (dependency.plugins.findPlugin(IntelliJPluginGr) == null) {
            throw new BuildException("Cannot use $dependency as a plugin dependency. IntelliJ Plugin is not found." + dependency.plugins, null)
        }
        dependencies.add(project.dependencies.create(dependency))
        def pluginDependency = new PluginProjectDependency(dependency)
        extension.addPluginDependency(pluginDependency)
        project.tasks.withType(PrepareSandboxTask).each {
            it.configureCompositePlugin(pluginDependency)
        }
    }
}
