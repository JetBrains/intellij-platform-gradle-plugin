val buildSearchableOptionsEnabledProperty = project.property("buildSearchableOptionsEnabled") == "true"

tasks {
    buildSearchableOptions {
        enabled = buildSearchableOptionsEnabledProperty
    }
}
