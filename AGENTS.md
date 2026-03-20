# AGENTS.md

## Cursor Cloud specific instructions

### Overview

This is the **EISOP Checker Framework** — a pluggable type-checking tool for Java. It is a compiler plugin (annotation processor) that detects bugs at compile time via enhanced type systems. It includes 24+ built-in type checkers (nullness, resource leaks, format strings, etc.) and a framework for building custom checkers.

This is a pure Java library — no databases, web servers, or external services are required.

### Prerequisites

- **JDK 21** (primary supported version; also tested on 8, 11, 17)
- `JAVA_HOME` must be set (e.g. `/usr/lib/jvm/java-21-openjdk-amd64`)
- The `/jdk` directory must exist and be writable — the build clones the [eisop/jdk](https://github.com/eisop/jdk) annotated JDK there (`framework/build.gradle` resolves `../../jdk` from `framework/`, which is `/jdk` when the workspace is at `/workspace`)

### Key commands

See `build.gradle` and subproject `build.gradle` files for the full set. Summary:

| Task | Command |
|------|---------|
| Fast build (for running javac) | `./gradlew assembleForJavac` |
| Full build (all jars incl. javadoc) | `./gradlew assemble` |
| Lint / formatting check | `./gradlew spotlessCheck` |
| Auto-format code | `./gradlew spotlessApply` |
| Run all JUnit tests | `./gradlew test` |
| Run a specific checker's tests | `./gradlew :checker:NullnessTest` |
| Run all tests (JUnit + non-JUnit + typecheck) | `./gradlew allTests` |
| Run the checker on sample code | `java -jar checker/dist/checker.jar -processor <checker> <file.java>` |

### Non-obvious caveats

- The build's `cloneAnnotatedJdk` task clones `eisop/jdk` to `/jdk` (parent of workspace root). If that directory doesn't exist or isn't writable, the build fails with `Failed to create directory '/jdk'`. Create it before building: `sudo mkdir -p /jdk && sudo chown $(whoami) /jdk`.
- The `installGitHooks` task runs on every `JavaCompile` task. It copies git hooks from `checker/bin-devel/` into `.git/hooks/`. This is normal and not an error.
- `./gradlew assembleForJavac` is much faster than `./gradlew assemble` if you only need to run the checker (no javadoc jars).
- Tests use significant heap (up to 4 GB per test JVM). The `NullnessTest` suite alone takes ~60-70 seconds.
- Gradle deprecation warnings about Gradle 9.0 incompatibility are expected and do not affect the build.
