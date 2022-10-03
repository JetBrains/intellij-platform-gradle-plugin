// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.model

import javax.xml.bind.annotation.XmlRegistry

@Suppress("unused")
@XmlRegistry
internal class ObjectFactory {

    fun createPluginsCache() = PluginsCache()

    fun createPluginVerifierRepository() = MavenMetadata()

    fun createProductsReleases() = ProductsReleases()

    fun createAndroidStudioReleases() = AndroidStudioReleases()
}
