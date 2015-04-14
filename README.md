# Overview
This plugin allows you to build plugins for intellij platform using specific IntelliJ SDK and bundled plugins.
The plugin adds extra IntelliJ-specific dependencies while compiling tasks.

# Usage

## Gradle >= 2.1

```groovy
plugins {
  id "org.jetbrains.intellij" version "0.0.2"
}
```

## Gradle < 2.1

```groovy
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath group: 'org.jetbrains', name: 'gradle-intellij-plugin', version: '1.0-SNAPSHOT'
  }
}

apply plugin: 'org.jetbrains.intellij'
```

## Configuration

Plugin provides following options to configure target IntelliJ SDK
- `idea.version` defines the version of IDEA distribution that should be used as a dependency. Default value: 142-SNAPSHOT
- `idea.plugins` defines the list of bundled IDEA plugins that should be used as dependencies. Default value: <empty>


### build.gradle
```groovy
plugins {
  id "org.jetbrains.intellij" version "1.0-SNAPSHOT"
}

repositories {
  maven { url "https://www.jetbrains.com/intellij-repository/releases" }
}

apply plugin: 'java'
apply plugin: 'org.jetbrains.intellij'

idea {
  version '14.1'
  plugins 'coverage'
}

sourceSets {
  main {
    java {
      srcDirs 'src', 'gen'
    }
    resources {
      srcDir 'resources'
    }
  }
  test {
    java {
      srcDir 'tests'
    }
    resources {
      srcDir 'testResources'
    }
  }
}

test {
  maxHeapSize = '512m'
  minHeapSize = '256m'
  enableAssertions = true
  jvmArgs '-XX:MaxPermSize=250m', '-Didea.system.path=system-test', '-Didea.config.path=config-test'
} 
```

# License
This plugin is available under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).