// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.Action
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceSpec
import kotlin.reflect.KClass

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
    configureAction: Action<BuildServiceSpec<P>> = Action { },
): Provider<T> {
    val serviceName = "${serviceClass.simpleName}_${serviceClass.java.classLoader.hashCode()}"
    return sharedServices.registerIfAbsent(serviceName, serviceClass.java, configureAction)
}
