## Quick start
Fist we need to launch Ide. Since `runIdeForUiTests` task is blocking, we can run it asynchronously.

`./gradlew ui-test-example:clean ui-test-example:runIdeForUiTests &`
 
Next we can start the tests. Because of this is local run, you must be sure welcome frame is visible on the screen. 

`./gradlew ui-test-example:test`

Or just run all together with one line

`./gradlew ui-test-example:clean ui-test-example:runIdeForUiTests & ./gradlew ui-test-example:test`

## Remote-robot

### Schema

### Searching components

### Executing code on Idea side

### Steps logging