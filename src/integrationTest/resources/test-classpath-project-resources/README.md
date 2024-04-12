# Test Classpath Project Resources

## Intention

The intention is to test that the project test resources take priority over resources provided by libraries
and IDE distribution JARs.

## Approach

The test project reproduces the usage of for mocking a final method with Mockito when the PowerMock dependency is
additionally configured in the project.

To be able to mock a final method Mockito must be provided with the inline MockMaker configuration,
which in our case is done by the `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` file.

When PowerMock is present, it provides its own `mockito-extensions/org.mockito.plugins.MockMaker` which can be loaded by
Mockito, if PowerMock is before project test resource in the classpath.
Such a situation caused the issue reported in:

- https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1101

## Verification

The test has two verifications:

1. Project test passes when a final method is mocked with Mockito. It would fail if the test resources directory was
   pushed after the PowerMock in the classpath.
2. The test resources directory is before any dependency and IDE distribution JAR in the classpath. We verify IDE JARs
   because they may provide MockMaker config in some JAR for any reason.
