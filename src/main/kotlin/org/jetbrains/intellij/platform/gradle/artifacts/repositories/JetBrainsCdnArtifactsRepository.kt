// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.repositories

import java.net.URI
import javax.inject.Inject

abstract class JetBrainsCdnArtifactsRepository @Inject constructor(
    name: String,
    url: URI,
    allowInsecureProtocol: Boolean = true,
) : BaseArtifactRepository(name, url, allowInsecureProtocol)
