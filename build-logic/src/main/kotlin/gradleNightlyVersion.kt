// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL

private const val GRADLE_NIGHTLY_JSON = "https://services.gradle.org/versions/nightly"

@Serializable
private data class GradleRelease(
    val version: String,
    val buildTime: String,
    val current: Boolean,
    val snapshot: Boolean,
    val nightly: Boolean,
    val releaseNightly: Boolean,
    val activeRc: Boolean,
    val rcFor: String,
    val milestoneFor: String,
    val broken: Boolean,
    val downloadUrl: String,
    val checksumUrl: String,
    val wrapperChecksumUrl: String
)

fun gradleNightlyVersion() =
    URL(GRADLE_NIGHTLY_JSON).readText().let {
        Json.decodeFromString<GradleRelease>(it)
    }.version
