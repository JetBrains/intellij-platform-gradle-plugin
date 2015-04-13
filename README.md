- idea.version – version of IDEA distribution that should be used as a dependency. Default value: 142-SNAPSHOT
- idea.plugins – list of bundled IDEA plugins that should be used as dependencies. Default value: <empty>

```
buildscript {
  ext.kotlin_version = '0.11.91.1'
  repositories {
    mavenCentral()
    maven {
      url uri('/Users/zolotov/dev/idea-gradle-plugin')
    }
  }
  dependencies {
    classpath "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
    classpath group: 'org.jetbrains.intellij', name: 'intellij-gradle-plugin', version: '1.0-SNAPSHOT'
  }
}

repositories {
  maven { url "https://www.jetbrains.com/intellij-repository/releases" }
}

apply plugin: 'java'
apply plugin: 'intellij-plugin'

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