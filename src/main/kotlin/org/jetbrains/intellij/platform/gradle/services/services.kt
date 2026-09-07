// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.Action
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.api.services.BuildServiceSpec
import kotlin.reflect.KClass

private fun <T : BuildService<*>> classLoaderScopedBuildServiceName(
    serviceClass: KClass<T>,
    projectPath: String?,
) = "${serviceClass.simpleName}_${serviceClass.java.classLoader.hashCode()}" +
        projectPath?.let { "_$it" }.orEmpty()

/**
 * Registers a classloader-scoped build service in the Gradle build lifecycle.
 * See: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1919#issuecomment-2848569816
 *
 * @param T The type of the build service to be registered, extending [BuildService].
 * @param P The type of the parameters for the build service, extending [BuildServiceParameters].
 * @param serviceClass The class of the build service to register.
 * @param configureAction An optional action to configure the build service's parameters.
 * @return A [Provider] wrapping the registered instance of the build service.
 */
internal fun <T : BuildService<P>, P : BuildServiceParameters> Gradle.registerClassLoaderScopedBuildService(
    serviceClass: KClass<T>,
    projectPath: String? = null,
    configureAction: Action<BuildServiceSpec<P>> = Action { },
): Provider<T> {
    val serviceName = classLoaderScopedBuildServiceName(serviceClass, projectPath)
    return sharedServices.registerIfAbsent(serviceName, serviceClass.java, configureAction)
}

/**
 * Registers a classloader-scoped build service and returns its shared parameters.
 *
 * Looking up a registration by its exact name is supported with Isolated Projects and lets independently configured
 * projects contribute configuration-cache-tracked values without accessing another project's model.
 */
internal fun <T : BuildService<P>, P : BuildServiceParameters> Gradle.registerClassLoaderScopedBuildServiceParameters(
    serviceClass: KClass<T>,
    projectPath: String? = null,
    configureAction: Action<BuildServiceSpec<P>> = Action { },
): P {
    val serviceName = classLoaderScopedBuildServiceName(serviceClass, projectPath)
    sharedServices.registerIfAbsent(serviceName, serviceClass.java, configureAction)

    @Suppress("UNCHECKED_CAST")
    val registration = sharedServices.registrations.findByName(serviceName) as? BuildServiceRegistration<T, P>
        ?: error("The '$serviceName' build service was not registered.")

    return registration.parameters
}
