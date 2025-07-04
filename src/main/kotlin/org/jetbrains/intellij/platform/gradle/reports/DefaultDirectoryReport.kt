// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.reports

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.reporting.Report
import org.gradle.api.tasks.OutputDirectory
import javax.inject.Inject

/**
 * A default implementation of [org.jetbrains.intellij.platform.gradle.reports.BaseReportContainer.BaseReport].
 *
 * Used by implementations of [org.gradle.api.reporting.ReportContainer] to represent a report that are written to a directory.
 *
 * @param name The logical name of the report, used for configuration and lookup.
 *
 * @see org.jetbrains.intellij.platform.gradle.reports.BaseReportContainer.BaseReport
 * @see org.gradle.api.reporting.Report.OutputType.DIRECTORY
 */
abstract class DefaultDirectoryReport @Inject constructor(name: String) :
    BaseReportContainer.BaseReport(name, Report.OutputType.DIRECTORY) {

    @OutputDirectory
    abstract override fun getOutputLocation(): DirectoryProperty
}