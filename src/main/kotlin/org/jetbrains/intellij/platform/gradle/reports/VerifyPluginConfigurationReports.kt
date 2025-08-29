// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.reports

import org.gradle.api.*
import org.gradle.api.model.ObjectFactory
import org.gradle.api.reporting.ReportContainer
import org.gradle.api.reporting.SingleFileReport
import org.gradle.api.reporting.internal.DefaultReportContainer
import org.gradle.api.reporting.internal.DefaultSingleFileReport
import org.gradle.api.reporting.internal.DelegatingReportContainer
import org.gradle.api.tasks.Nested
import org.jetbrains.kotlin.com.google.common.collect.ImmutableList
import javax.inject.Inject

/**
 * Provides a container for the output reports of verifying the plugin configuration.
 *
 * @param owner The entity responsible for these reports, typically the associated task.
 * @param objectFactory Used to instantiate and manage report objects.
 * @see VerifyPluginConfigurationReports
 * @see DelegatingReportContainer
 * @see DefaultReportContainer
 * @see DefaultSingleFileReport
 */
open class VerifyPluginConfigurationReportsImpl @Inject constructor(
    owner: Describable,
    objectFactory: ObjectFactory,
) : DelegatingReportContainer<SingleFileReport>(
    DefaultReportContainer.create(
        objectFactory,
        SingleFileReport::class.java
    ) { factory ->
        val list: Collection<SingleFileReport> = ImmutableList.of(
            factory.instantiateReport(DefaultSingleFileReport::class.java, "txt", owner)
        )

        list
    }

), VerifyPluginConfigurationReports {

    override val txt: SingleFileReport
        get() = getByName("txt")
}

/**
 * Represents a container for output reports of verifying the plugin configuration.
 *
 * Implementations are responsible for supplying the actual report instances.
 *
 * Typical usage is to access the desired report format for further configuration or output handling.
 * @see ReportContainer
 * @see SingleFileReport
 * @see org.gradle.api.reporting.Reporting
 */
interface VerifyPluginConfigurationReports: ReportContainer<SingleFileReport> {
    @get:Nested
    val txt: SingleFileReport
}
