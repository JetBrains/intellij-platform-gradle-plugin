val intellijVersionProperty = project.property("intellijVersion").toString()

intellij {
    version.set(intellijVersionProperty)
}
