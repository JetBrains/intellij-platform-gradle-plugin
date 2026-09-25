// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.test.*

class ExtractorTransformerTest {

    @Test
    fun `ExtractorTransformer is annotated with DisableCachingByDefault`() {
        val disableCaching = ExtractorTransformer::class.findAnnotation<DisableCachingByDefault>()
        val cacheableTransform = ExtractorTransformer::class.findAnnotation<CacheableTransform>()

        assertNotNull(disableCaching, "ExtractorTransformer must be annotated with @DisableCachingByDefault")
        assertEquals("Not worth caching", disableCaching.because)
        assertNull(cacheableTransform, "ExtractorTransformer must not be annotated with @CacheableTransform")
    }

    @Test
    fun `ExtractorTransformer inputArtifact has InputArtifact and PathSensitive NAME_ONLY annotations`() {
        val property = ExtractorTransformer::class.memberProperties.find { it.name == "inputArtifact" }
        assertNotNull(property, "inputArtifact property must exist")

        val inputArtifact = property.getter.findAnnotation<InputArtifact>()
        val pathSensitive = property.getter.findAnnotation<PathSensitive>()

        assertNotNull(inputArtifact, "inputArtifact must be annotated with @InputArtifact")
        assertNotNull(pathSensitive, "inputArtifact must be annotated with @PathSensitive")
        assertEquals(PathSensitivity.NAME_ONLY, pathSensitive.value)
    }

    @Test
    fun `ExtractorTransformer parameters extractorService is marked as Internal`() {
        val property = ExtractorTransformer.Parameters::class.memberProperties.find { it.name == "extractorService" }
        assertNotNull(property, "extractorService property must exist")

        val internalAnnotation = property.getter.findAnnotation<Internal>()
        assertNotNull(internalAnnotation, "extractorService must be annotated with @Internal")
    }
}
