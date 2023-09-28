// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.model

import javax.xml.bind.annotation.XmlRegistry

@Suppress("unused")
@XmlRegistry
class ObjectFactory {

    fun createPluginsCache() = PluginsCache()

    fun createPluginVerifierRepository() = MavenMetadata()

    fun createProductsReleases() = ProductsReleases()

    fun createAndroidStudioReleases() = AndroidStudioReleases()

    fun createIvyModule() = IvyModule()
}
