// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts

import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.resolve.RulesMode
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.all
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Dependencies
import org.jetbrains.intellij.platform.gradle.localPlatformArtifactsPath
import org.jetbrains.intellij.platform.gradle.models.IvyModulePublicationsOnly
import org.jetbrains.intellij.platform.gradle.models.xml
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import org.jetbrains.intellij.platform.gradle.utils.writeTextAtomicallyIfChanged
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.io.path.notExists

internal fun decodeIvyModulePublications(input: String) =
    xml.decodeFromString(IvyModulePublicationsOnly.serializer(), input).publications

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
 * The rule is registered once per project, eagerly, when the plugin is applied — see [register].
 * Gradle (since 9.8.0) takes an immutable snapshot of the component metadata rules when a resolution starts,
 * so a rule added later (for example, from an `afterResolve` of the IntelliJ Platform configuration, which may be resolved
 * in the middle of the `compileClasspath` resolution while realizing lazy bundled plugin dependencies) is never applied
 * to that resolution.
 *
 * Because the IntelliJ Platform location is not known at registration time, it is not passed as a rule parameter.
 * Instead, the location of the IntelliJ Platform for the given Ivy version (like `IU-253.33813.55`) is stored in a
 * [PLATFORM_PATH_FILE_NAME] file next to the Ivy XML files of that version, see [writePlatformPath].
 * That file is not part of the Ivy metadata, so the Ivy XML files stay portable.
 *
 * The rule is intentionally not a [org.gradle.api.artifacts.CacheableRule]:
 * its outcome depends on the [PLATFORM_PATH_FILE_NAME] file content, which is not a rule input known to Gradle,
 * so a cached result could point to an outdated IntelliJ Platform location.
 *
 * @see org.jetbrains.intellij.platform.gradle.models.IvyModule
 * @see org.jetbrains.intellij.platform.gradle.plugins.project.IntelliJPlatformBasePlugin.apply
 */
@Suppress("KDocUnresolvedReference")
abstract class LocalIvyArtifactPathComponentMetadataRule @Inject constructor(
    private val absNormalizedIvyPath: String,
) : ComponentMetadataRule {

    private val log = Logger(javaClass)

    override fun execute(context: ComponentMetadataContext) {
        val id = context.details.id
        // Since we also need to fix transitive dependencies, we have to intercept everything and filter.
        if (id.group !in REPLACEMENT_GROUPS) {
            return
        }

        // Not cached, as the IntelliJ Platform location of the given version may change between builds.
        val platformPathFile = File("$absNormalizedIvyPath/${id.version}/$PLATFORM_PATH_FILE_NAME")
        if (!platformPathFile.exists()) {
            log.error("The IntelliJ Platform location of the $id module is unknown, the following file is missing: ${platformPathFile.path}")
            return
        }
        val absNormalizedPlatformPath = platformPathFile.readText()

        /**
         * Unfortunately, Gradle here doesn't expose anything from Ivy metadata, all we know is: group, name and version.
         * Much more is visible in debug, but all that is private.
         * So we have to read the Ivy XML again.
         *
         * We only need publication artifacts for path fixing. Parsing the whole Ivy module is unnecessary
         * and may fail if dependency entries contain unexpected metadata.
         */
        val ivyXmlFile = File("$absNormalizedIvyPath/${id.version}/${id.group}-${id.name}-${id.version}.xml")
        val publications = ivyPublicationsCache.computeIfAbsent(ivyXmlFile.path) {
            decodeIvyModulePublications(ivyXmlFile.readText())
        }

        context.details.allVariants {
            withFiles {
                // Remove all existing artifacts because they have relative paths and won't be found.
                removeAllFiles()

                // Add new files (i.e., artifacts) with the correct absolute path.
                publications.forEach { artifact ->
                    val fileName = "${artifact.name}.${artifact.ext}"
                    val absPathString = "$absNormalizedPlatformPath/${artifact.url}/$fileName"

                    if (Path.of(absPathString).notExists()) {
                        log.error("The following artifact of the $id module ${artifact.name} is not found: $absPathString")
                        return@forEach
                    }

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
        private val REPLACEMENT_GROUPS = setOf(Dependencies.BUNDLED_PLUGIN_GROUP, Dependencies.BUNDLED_MODULE_GROUP)
        private val ivyPublicationsCache = ConcurrentHashMap<String, List<org.jetbrains.intellij.platform.gradle.models.IvyModule.Artifact>>()

        /**
         * Name of the file, stored next to the Ivy XML files of a given version, which contains the absolute path of the IntelliJ Platform
         * that the bundled plugins and modules of this version belong to.
         * It never matches the Ivy pattern of the local Ivy repository, so Gradle doesn't treat it as a metadata file.
         */
        internal const val PLATFORM_PATH_FILE_NAME = "platform-path.txt"

        /**
         * Stores the [platformPath] location for the given Ivy [version], so [LocalIvyArtifactPathComponentMetadataRule] can resolve
         * relative artifact paths of bundled plugins and modules of that version.
         *
         * @param ivyPath The local Ivy repository location.
         * @param version The Ivy version of bundled plugins and modules, like `IU-253.33813.55`.
         * @param platformPath The IntelliJ Platform location.
         */
        internal fun writePlatformPath(ivyPath: Path, version: String, platformPath: Path) {
            ivyPath.resolve(version).resolve(PLATFORM_PATH_FILE_NAME).writeTextAtomicallyIfChanged(platformPath.safePathString)
        }

        /**
         * Registers the rule eagerly, before any configuration is resolved, so it is part of every resolution in the project.
         */
        internal fun register(
            dependencies: DependencyHandler,
            providers: ProviderFactory,
            settings: Settings,
            rootProjectDirectory: Path
        ) {
            val log = Logger(javaClass)
            val ruleName = LocalIvyArtifactPathComponentMetadataRule::class.simpleName
            // Settings are fully evaluated before any project is configured, so the value is final here.
            val rulesMode = settings.dependencyResolutionManagement.rulesMode.get()

            if (RulesMode.PREFER_PROJECT == rulesMode) {
                val ivyLocationPath = providers.localPlatformArtifactsPath(rootProjectDirectory).get().safePathString

                dependencies.components.all<LocalIvyArtifactPathComponentMetadataRule> {
                    params(ivyLocationPath)
                }

                log.info("$ruleName has been registered.")
            } else {
                log.info("$ruleName can not be registered because '${rulesMode}' mode is used in settings.")
            }
        }
    }
}
