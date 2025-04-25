# GitHub Workflows

## Matrix testing

`reusable-unitTests.yml`

Gradle versions:

- minimum supported version (`8.5`, see `IntelliJPluginConstants.Constraints.getMINIMAL_GRADLE_VERSION`)
- latest GA (e.g., `8.14`)
- RC of next GA (potentially)

When updating _latest GA_ version, adjust `exclude` section 
and adjust `single-unitTest.yml`.
