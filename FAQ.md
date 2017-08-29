#### How to modify jvmArguments of runIdea task

`runIde` task is a [Java Exec](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.JavaExec.html) task and can be modified according to the documentation.

To add some jvm arguments while launching IDE, configure `runIde` task in a following way:

```
runIde {
  jvmArgs '-DmyProperty=value'
}
```

####  How to modify system properties of runIdea task

Using the [very same task documentation](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.JavaExec.html), configure `runIde` task:

```
runIde {
  systemProperty('name', 'value' )
}
```

#### How to Debug

Running gradle tasks from IDEA produces Gradle run configuration. The produced configuration can be run in debug mode just as any other run configuration:

![Debug Gradle run configuration](https://cloud.githubusercontent.com/assets/140920/9789780/ca31d9f2-57da-11e5-804b-087b06a6eda9.png)
