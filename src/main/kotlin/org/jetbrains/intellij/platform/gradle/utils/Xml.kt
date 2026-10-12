// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import javax.xml.stream.XMLInputFactory

internal val xmlInputFactory: ThreadLocal<XMLInputFactory> = ThreadLocal.withInitial {
    XMLInputFactory.newFactory().apply {
        runCatching { setProperty(XMLInputFactory.SUPPORT_DTD, false) }
        runCatching { setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false) }
    }
}
