// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
rootProject.name = "test"

buildscript {
    // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1778
    // https://docs.gradle.org/current/userguide/dependency_locking.html
    dependencyLocking {
        lockAllConfigurations()
        lockFile = file("gradle/locks/root/settings-gradle-buildscript.lockfile")
        lockMode.set(LockMode.DEFAULT)
        //ignoredDependencies.add()
    }
}
