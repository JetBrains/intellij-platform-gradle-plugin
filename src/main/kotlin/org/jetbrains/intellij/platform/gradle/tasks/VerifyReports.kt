// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.*
import org.gradle.api.model.ObjectFactory
import org.gradle.api.reporting.DirectoryReport
import org.gradle.api.reporting.ReportContainer
import org.gradle.api.reporting.internal.DefaultReportContainer
import org.gradle.api.reporting.internal.DelegatingReportContainer
import org.gradle.api.reporting.internal.SingleDirectoryReport
import org.gradle.api.tasks.Internal
import org.jetbrains.kotlin.com.google.common.collect.ImmutableList
import javax.inject.Inject

/**
 * Provides a container for plugin verification reports in various formats.
 *
 * This implementation supplies multiple report types for plugin verification tasks,
 * leveraging Gradle's reporting infrastructure to manage and configure them.
 *
 * The class is designed to be extensible and maintainable, allowing for the addition
 * or modification of report formats as requirements evolve. It delegates report management
 * to Gradle's container mechanisms, ensuring consistent behavior and integration with
 * Gradle's reporting APIs.
 *
 * @param owner The entity responsible for these reports, typically the associated task.
 * @param objectFactory Used to instantiate and manage report objects.
 * @see VerifyReports
 * @see ReportContainer
 * @see org.gradle.api.reporting.Reporting
 * @see SingleDirectoryReport
 */
open class VerifyReportsImpl @Inject constructor(
    owner: Describable?,
    objectFactory: ObjectFactory,
) : DelegatingReportContainer<DirectoryReport>(
    DefaultReportContainer.create(
        objectFactory,
        DirectoryReport::class.java
    ) { factory ->
        val list: Collection<DirectoryReport> = ImmutableList.of(
            factory.instantiateReport(SingleDirectoryReport::class.java, "plain", owner, null),
            factory.instantiateReport(SingleDirectoryReport::class.java, "html", owner, null),
            factory.instantiateReport(SingleDirectoryReport::class.java, "markdown", owner, null)
        )

        list
    }

), VerifyReports {

    override val plain: DirectoryReport
        get() = getByName("plain")

    override val html: DirectoryReport
        get() = getByName("html")

    override val markdown: DirectoryReport
        get() = getByName("markdown")
}

/**
 * Represents a container for multiple plugin verification report formats.
 *
 * Provides access to standard report types (such as plain text, HTML, and Markdown)
 * generated during plugin verification. The specific formats and their implementations
 * may evolve, but this interface ensures a consistent way to retrieve available reports.
 *
 * Implementations are responsible for supplying the actual report instances.
 *
 * Typical usage is to access the desired report format for further configuration or output handling.

 * @see org.gradle.api.reporting.ReportContainer
 * @see org.gradle.api.reporting.DirectoryReport
 * @see org.gradle.api.reporting.Reporting
 */
interface VerifyReports: ReportContainer<DirectoryReport> {
    @get:Internal
    val html: DirectoryReport

    @get:Internal
    val plain: DirectoryReport

    @get:Internal
    val markdown: DirectoryReport

}