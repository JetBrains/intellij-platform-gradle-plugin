// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts

import nl.adaptivity.xmlutil.serialization.XML
import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.initialization.resolve.RulesMode
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.all
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Dependencies
import org.jetbrains.intellij.platform.gradle.extensions.localPlatformArtifactsPath
import org.jetbrains.intellij.platform.gradle.models.IvyModule
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.platformPath
import java.io.File
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.absolute
import kotlin.io.path.invariantSeparatorsPathString

/**
 * This comes into play only when [org.gradle.api.initialization.resolve.RulesMode.PREFER_PROJECT] (the default) is used in Gradle's settings.
 *
 * Fixes relative URLs of dependencies from the local Ivy repository [org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesHelper.createLocalIvyRepository]
 * by appending the full absolute path.
 * It is necessary only for [Dependencies.BUNDLED_PLUGIN_GROUP] and [Dependencies.BUNDLED_MODULE_GROUP] dependency types.
 *
 *  For [Dependencies.BUNDLED_PLUGIN_GROUP] and [Dependencies.BUNDLED_MODULE_GROUP], we expect:
 *
 *  - "artifact" ([org.jetbrains.intellij.platform.gradle.models.IvyModule.Artifact.name]) is mandatory and contains only the name of the artifact (for example, a jar archive), without the extension.
 *
 *  - "url" ([org.jetbrains.intellij.platform.gradle.models.IvyModule.Artifact.url]) contains a path, relative to the platformPath (IDE), without the file's name.
 *    According to Ivy's [documentation](https://ant.apache.org/ivy/history/latest-milestone/ivyfile/artifact.html)
 *    > a URL at which this artifact can be found if it isn’t located at the standard location in the repository
 *
 *    It may be not the best to field to put this into, but there is no alternative.
 *
 *    The reason why we put the path into "url" is that the name shouldn't have it, because:
 *     - Artifact name may come up in files like Gradle's `verification-metadata.xml` which will make them not portable between different environments.
 *       - https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1778
 *       - https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1779
 *       - https://docs.gradle.org/current/userguide/dependency_verification.html
 *     - Artifact name may also come up in Gradle errors, for example, if for some reason the artifact is not resolved.
 *       In that case, the artifact coordinates may look very weird like: `bundledPlugin:/some/path/more/path/some.jar:123.456.789`
 *       For the same reason file extension is also stored in "ext".
 *
 *  - "ext" [org.jetbrains.intellij.platform.gradle.models.IvyModule.Artifact.ext] is an optional file extension, like "jar".
 *    It is optional only because files don't always have extensions.
 *    For directories, it would be "directory", but in this case, we never expect to have a directory, always only files.
 *
 * Relative paths are better than absolute because if Gradle's dependency verification is used with metadata (for example, `ivy.xml` or `pom.xml`) files
 * verification enabled, hashes of these files will be the same in different environments, despite that they're stored in different locations.
 * If absolute paths are used, they will be mentioned in `ivy.xml` thus changing the hash on each env.
 *
 * But since our local Ivy repository has an artifact pattern `/[artifact]` relative URLs won't work.
 * See [org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesHelper.createLocalIvyRepository].
 * That is why this class is needed.
 *
 * This is called after Ivy XML metadata is already found and parsed, so all dependencies and publications are known,
 * but not yet resolved on the file system, so we have a chance to fix the paths.
 *
 * A separate note on [org.gradle.api.artifacts.CacheableRule], since there is not enough information on it in the internet.
 * First, see the comments in [org.gradle.internal.resolve.caching.CachingRuleExecutor].
 * I tried to debug it, and it seems like rule parameters are taken into account.
 * It happens in [org.gradle.internal.resolve.caching.CrossBuildCachingRuleExecutor.computeExplicitInputsSnapshot]
 * Which should mean that we can use the caching, because if the paths change, it should be re-evaluated.
 *
 * @see org.jetbrains.intellij.platform.gradle.models.IvyModule
 * @see org.jetbrains.intellij.platform.gradle.plugins.project.IntelliJPlatformBasePlugin.apply
 */
@CacheableRule
abstract class LocalIvyArtifactPathComponentMetadataRule @Inject constructor(
    private val absNormalizedPlatformPath: String,
    private val absNormalizedIvyPath: String,
) : ComponentMetadataRule {

    private val replacementGroups = listOf(Dependencies.BUNDLED_PLUGIN_GROUP, Dependencies.BUNDLED_MODULE_GROUP)

    override fun execute(context: ComponentMetadataContext) {
        val id = context.details.id
        // Since we also need to fix transitive dependencies, we have to intercept everything and filter.
        if (id.group !in replacementGroups) {
            return
        }

        context.details.allVariants {
            withFiles {
                /**
                 * Unfortunately, Gradle here doesn't expose anything from Ivy metadata, all we know is: group, name and version.
                 * Much more is visible in debug, but all that is private.
                 * So we have to read the Ivy XML again.
                 */
                val ivyXmlFile = File("$absNormalizedIvyPath/${id.version}/${id.group}-${id.name}-${id.version}.xml")
                val ivyModule = XML.Companion.decodeFromString<IvyModule>(ivyXmlFile.readText())

                // Remove all existing artifacts because they have relative paths and won't be found.
                removeAllFiles()

                // Add new files (i.e., artifacts) with the correct absolute path.
                ivyModule.publications.forEach { artifact ->
                    val fileName = "${artifact.name}.${artifact.ext}"
                    val absPathString = "$absNormalizedPlatformPath/${artifact.url}/$fileName"

                    /**
                     * It is important to pass in the name and absolute path as the second arg, instead of just `addFile(absPathString)`,
                     * because when only the path is given, Gradle thinks it downloads a file from a URL and copied all artifacts into
                     * `~/.gradle/caches/modules-2/files-2.1/`.
                     */

                    if (OperatingSystem.current().isWindows) {
                        /**
                         * On Windows we should add a leading slash because there absolute paths start from a drive letter, but if Gradle sees such path
                         * (without a leading slash), it will treat it relative to the build dir and absPathString will become malformed like:
                         * `C:/Users/user-name/AppData/Local/Temp/tmp2087252038786353695/D:/project/.gradle/caches/8.10.2/transforms/137db90ba7a52eac7de798d9291575dd/transformed/ideaIC-2022.3.3-win/plugins/copyright/lib/copyright.jar`
                         *
                         * But this option works well:
                         * `/D:/project/.gradle/caches/8.10.2/transforms/137db90ba7a52eac7de798d9291575dd/transformed/ideaIC-2022.3.3-win/plugins/copyright/lib/copyright.jar`
                         */
                        addFile(fileName, "/$absPathString")
                    } else {
                        /**
                         * On Linux and OSX absolute paths start from slash naturally.
                         */
                        addFile(fileName, absPathString)
                    }
                }
            }
        }
    }

    companion object {
        internal fun register(
            configuration: Configuration,
            dependencies: DependencyHandler,
            providers: ProviderFactory,
            settings: SettingsInternal,
            rootProjectDirectory: Path
        ) {
            configuration.incoming.afterResolve {
                val log = Logger(javaClass)
                val ruleName = LocalIvyArtifactPathComponentMetadataRule::class.simpleName
                // Intentionally delaying the check just in case if it changes somehow late in the lifecycle.
                val rulesMode = settings.dependencyResolutionManagement.rulesMode.get()

                if (RulesMode.PREFER_PROJECT == rulesMode) {
                    if (configuration.resolvedConfiguration.hasError()) {
                        log.warn("Configuration '${Configurations.INTELLIJ_PLATFORM_DEPENDENCY}' has some resolution errors. $ruleName will not be registered.")
                    } else if (configuration.isEmpty) {
                        log.warn("Configuration '${Configurations.INTELLIJ_PLATFORM_DEPENDENCY}' is empty. $ruleName will not be registered.")
                    } else {
                        val artifactLocationPath = configuration.platformPath()
                            .absolute()
                            .normalize()
                            .invariantSeparatorsPathString
                        val ivyLocationPath = providers.localPlatformArtifactsPath(rootProjectDirectory)
                            .absolute()
                            .normalize()
                            .invariantSeparatorsPathString

                        dependencies.components.all<LocalIvyArtifactPathComponentMetadataRule> {
                            params(artifactLocationPath, ivyLocationPath)
                        }

                        log.info("$ruleName has been registered.")
                    }
                } else {
                    log.info("$ruleName can not be registered because '${rulesMode}' mode is used in settings.")
                }
            }
        }
    }
}