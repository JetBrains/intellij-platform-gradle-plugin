// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.reports

import groovy.lang.Closure
import org.gradle.api.*
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.reporting.Report
import org.gradle.api.reporting.ReportContainer
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Internal
import org.gradle.util.internal.ConfigureUtil
import java.util.*

/**
 * Base implementation of [ReportContainer] for use with reporting-based Gradle tasks.
 *
 * Provides a named domain object set to manage and configure reports of type [T], typically extending [org.gradle.api.reporting.Report].
 *
 * This class supports basic Gradle reporting operations and enforces immutability for write operations.
 *
 * Subclasses are expected to register one or more concrete classes of the Report class(e.g., [DefaultDirectoryReport]) during initialization.
 *
 * @param T The specific report type managed by this container.
 * @param objectFactory Gradle's [ObjectFactory] used to instantiate report instances.
 * @param clazz The Java class of the report type being stored.
 *
 * @see org.gradle.api.reporting.Reporting
 * @see org.gradle.api.reporting.Report
 * @see org.gradle.api.reporting.ReportContainer
 * @see <a href="https://github.com/gradle/gradle/issues/7063">Gradle issue #7063 on reporting extensibility</a>
 * @see <a href="https://docs.gradle.org/current/dsl/org.gradle.api.reporting.ReportContainer.html">Docs on ReportContainer</a>
 */
open class BaseReportContainer<T : BaseReportContainer.BaseReport>(objectFactory: ObjectFactory, clazz: Class<T>) : ReportContainer<T> {
    private val reports = objectFactory.namedDomainObjectSet(clazz)
    private val enabledReports = reports.matching { report: T -> report.required.get() }

    @get:Internal
    override val size: Int
        get() = reports.size

    override fun getEnabled(): NamedDomainObjectSet<T?> {
        return this.enabledReports
    }

    final override fun add(element: T): Boolean {
        return reports.add(element)
    }

    override fun addAll(elements: Collection<T>): Boolean {
        throw ReportContainer.ImmutableViolationException()
    }

    override fun addLater(provider: Provider<out T>) {
        throw ReportContainer.ImmutableViolationException()
    }

    override fun addAllLater(provider: Provider<out Iterable<T>?>) {
        throw ReportContainer.ImmutableViolationException()
    }

    override fun remove(element: T): Boolean {
        throw ReportContainer.ImmutableViolationException()
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        throw ReportContainer.ImmutableViolationException()
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        throw ReportContainer.ImmutableViolationException()
    }

    override fun clear() {
        throw ReportContainer.ImmutableViolationException()
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        return reports.containsAll(elements)
    }

    override fun getNamer(): Namer<T?> {
        return Namer { obj: T? -> obj!!.name }
    }

    override fun getAsMap(): SortedMap<String, T?> {
        return reports.asMap
    }

    override fun getNames(): SortedSet<String> {
        return reports.names
    }

    override fun findByName(name: String): T? {
        return reports.findByName(name)
    }

    @Throws(UnknownDomainObjectException::class)
    override fun getByName(name: String): T {
        return reports.getByName(name)
    }

    @Throws(UnknownDomainObjectException::class)
    override fun getByName(name: String, configureClosure: Closure<*>): T {
        return reports.getByName(name, configureClosure)
    }

    @Throws(UnknownDomainObjectException::class)
    override fun getByName(name: String, configureAction: Action<in T>): T {
        return reports.getByName(name, configureAction)
    }

    @Throws(UnknownDomainObjectException::class)
    override fun getAt(name: String): T {
        return reports.getAt(name)
    }

    override fun addRule(rule: Rule): Rule {
        return reports.addRule(rule)
    }

    override fun addRule(description: String, ruleAction: Closure<*>): Rule {
        return reports.addRule(description, ruleAction)
    }

    override fun addRule(description: String, ruleAction: Action<String>): Rule {
        return reports.addRule(description, ruleAction)
    }

    override fun getRules(): List<Rule> {
        return reports.rules
    }

    override fun isEmpty(): Boolean {
        return reports.isEmpty()
    }

    override fun contains(element: T): Boolean {
        return reports.contains(element)
    }

    override fun iterator(): MutableIterator<T?> {
        return reports.iterator()
    }

    override fun getEnabledReports(): Map<String, T?> {
        return enabledReports.asMap
    }

    override fun <S : T?> withType(type: Class<S>): NamedDomainObjectSet<S> {
        return reports.withType(type)
    }

    override fun <S : T?> withType(
        type: Class<S>,
        configureAction: Action<in S>
    ): DomainObjectCollection<S> {
        return reports.withType(type, configureAction)
    }

    override fun <S : T?> withType(type: Class<S>, configureClosure: Closure<*>): DomainObjectCollection<S> {
        return reports.withType(type, configureClosure)
    }

    override fun matching(spec: Spec<in T>): NamedDomainObjectSet<T?> {
        return reports.matching(spec)
    }

    override fun matching(spec: Closure<*>): NamedDomainObjectSet<T?> {
        return reports.matching(spec)
    }

    override fun whenObjectAdded(action: Action<in T>): Action<in T?> {
        return reports.whenObjectAdded(action)
    }

    override fun whenObjectAdded(action: Closure<*>) {
        reports.whenObjectAdded(action)
    }

    override fun whenObjectRemoved(action: Action<in T>): Action<in T?> {
        return reports.whenObjectRemoved(action)
    }

    override fun whenObjectRemoved(action: Closure<*>) {
        reports.whenObjectRemoved(action)
    }

    override fun all(action: Action<in T>) {
        reports.all(action)
    }

    override fun all(action: Closure<*>) {
        reports.all(action)
    }

    override fun configureEach(action: Action<in T>) {
        reports.configureEach(action)
    }

    @Throws(UnknownDomainObjectException::class)
    override fun named(name: String): NamedDomainObjectProvider<T?> {
        return reports.named(name)
    }

    @Throws(UnknownDomainObjectException::class)
    override fun named(
        name: String,
        configurationAction: Action<in T>
    ): NamedDomainObjectProvider<T?> {
        return reports.named(name, configurationAction)
    }

    @Throws(UnknownDomainObjectException::class)
    override fun <S : T?> named(
        name: String,
        type: Class<S>
    ): NamedDomainObjectProvider<S> {
        return reports.named(name, type)
    }

    @Throws(UnknownDomainObjectException::class)
    override fun <S : T?> named(
        name: String, type: Class<S>,
        configurationAction: Action<in S>
    ): NamedDomainObjectProvider<S> {
        return reports.named(name, type, configurationAction)
    }

    override fun named(nameFilter: Spec<String>): NamedDomainObjectSet<T?> {
        return reports.named(nameFilter)
    }

    override fun getCollectionSchema(): NamedDomainObjectCollectionSchema {
        return reports.collectionSchema
    }

    override fun findAll(spec: Closure<*>): Set<T?> {
        return reports.findAll(spec)
    }

    override fun configure(closure: Closure<*>): ReportContainer<T> {
        val cl = closure.clone() as Closure<*>
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        cl.delegate = this
        cl.call(this)
        return this
    }

    /**
     * The base implementation of a Gradle [Report]
     *
     * Used as a foundation for reports generated by tasks like [org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask].
     *
     * @param name The logical name of the report (e.g., `plain`, `html`).
     * @param outputType The format in which the report will be generated (e.g., `FILE`, `DIRECTORY`).
     *
     * @see Report
     * @see org.gradle.api.reporting.Reporting
     */
    abstract class BaseReport(
        private val name: String,
        private val outputType: Report.OutputType
    ) : Report {
        override fun getDisplayName(): String = name

        override fun getName(): String = name

        abstract override fun getOutputLocation(): FileSystemLocationProperty<out FileSystemLocation>

        override fun getOutputType(): Report.OutputType = outputType

        override fun configure(cl: Closure<*>?): Report = ConfigureUtil.configureSelf(cl, this)
    }
}