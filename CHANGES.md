# Changelog

## 0.2.0 SNAPSHOT

## 0.1

### 0.1.10

**Avoid using this version unless you have several plugin project which use the very same sandbox directory**

- Do not override plugins directory content (temporary fix of #17) 

### 0.1.9

- Added default configuration to ivy-repositories (fixes #114)

### 0.1.6

- External plugin directories are placed in compile classpath so IDEA code insight is better for them now (fixes #105) 

### 0.1.4

- Fix incremental compilation on changing `intellij.version` (fixes #67)

### 0.1.0

- Support external plugin dependencies


## 0.0

### 0.0.41

- Fix Kotlin forms instrumentation (#73)

### 0.0.39

- Allow to make single-build plugin distributions (fixes #64)

### 0.0.37

- Exclude kotlin dependencies if needed (fixes #57)

### 0.0.35

- Disable automatic updates check in debug IDEA (fixes #46)

### 0.0.34

- Support local IDE installation as a target application of `runIdea` task

### 0.0.33

- Attach community sources to ultimate IntelliJ artifact (fixes #37)
- New extension for passing system properties to `runIdea` task (fixes #18)

### 0.0.32

- Support compilation in IDEA 13.1 (fixes #28)

### 0.0.30

- Fixed broken `runIdea` task

### 0.0.29

- `cleanTest` task clean `system-test` and `config-test` directories (fixes #13)
- Do not override plugins which were installed in debug IDEA (fixes #24)

### 0.0.28

- `RunIdeaTask` is extensible (fixes #23)
- Fix xml parsing exception (fixes #25)

### 0.0.27

- Disabled custom class loader in tests (fixes #21)

### 0.0.25

- Do not patch version tag if `project.version` property is not specified (fixes #11)

### 0.0.21

- IntelliJ-specific jars are attached as compile dependency (fixes #5)

### 0.0.10

- Support for attaching IntelliJ sources in IDEA