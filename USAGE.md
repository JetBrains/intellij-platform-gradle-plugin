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
    classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
    classpath group: 'org.jetbrains.intellij', name: 'idea-gradle-plugin', version: '1.0-SNAPSHOT'
  }
}

apply plugin: 'org.jetbrains.intellij.idea-gradle-plugin'
```