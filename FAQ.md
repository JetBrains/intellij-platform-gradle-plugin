#### How to modify jvmArguments of runIde task

`runIde` task is a [Java Exec](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.JavaExec.html) task and can be modified according to the documentation.

To add some jvm arguments while launching IDE, configure `runIde` task in a following way:

```
runIde {
  jvmArgs '-DmyProperty=value'
}
```

####  How to modify system properties of runIde task

Using the [very same task documentation](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.JavaExec.html), configure `runIde` task:

```
runIde {
  systemProperty('name', 'value' )
}
```

#### How to Debug

Running gradle tasks from IDEA produces Gradle run configuration. The produced configuration can be run in debug mode just as any other run configuration:

![Debug Gradle run configuration](https://cloud.githubusercontent.com/assets/140920/9789780/ca31d9f2-57da-11e5-804b-087b06a6eda9.png)

#### How do I add my a custom file inside plugin distribution

`prepareSandbox` task is a [Sync](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.Sync.html) task and can be modified accordinally. Something like following should work:

```
prepareSandbox {
  from('yourFile') { 
    into "${intellij.pluginName}/lib/" 
  }
}
```

#### How to configure logging

The most convenient way to see the logs of running IDE is to add a tab to Run tool window with content of idea.log file:

![Logs](https://intellij-support.jetbrains.com/hc/user_images/GazJhC54rML33MBauVXrww.png)

To do this you need to add log file in Gradle run configuration settings:

![Gradle run configuration](https://intellij-support.jetbrains.com/hc/user_images/qPiO-BjDP_fSIPKJ5VePJA.png)
