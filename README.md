# Physics Factory

Desktop application that will generate satisfying physics videos with Blender automation.

**This repository currently implements Story 1.1 only: the runtime foundation.** On startup the
application provisions its workspace, verifies the configured Blender installation, prints what it
found, and stops with an actionable message if the environment is unusable. There is deliberately no
rendering, AI generation, physics simulation, upload or UI code yet.

---

## Requirements

| Tool    | Version | Notes                                                                        |
|---------|---------|------------------------------------------------------------------------------|
| JDK     | 21      | Gradle uses a Java 21 toolchain; the JDK running Gradle only needs to be 17+. |
| Gradle  | 9.7     | Provided by the wrapper. Any 8.14+ or 9.x release works.                      |
| Blender | 4.x     | Must be installed and executable. Not bundled.                               |

You do not need to install JDK 21 by hand. `settings.gradle.kts` applies the
`org.gradle.toolchains.foojay-resolver-convention` plugin, so if no Java 21 toolchain is found on the
machine, Gradle downloads a Temurin 21 into its own toolchain store on the first build and reuses it
afterwards. An already-installed JDK 21 is always preferred over downloading. The first build with
auto-provisioning needs network access to `api.foojay.io`; if that is blocked in your environment,
install a JDK 21 instead (`brew install temurin@21`, `sdk install java 21-tem`, or IntelliJ's
*Download JDK…*).

---

## Folder structure

```
PhysicsSimulation/
├── build.gradle.kts                  # Gradle Kotlin DSL build
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/
│   └── gradle-wrapper.properties     # see "Gradle wrapper" below
├── .gitignore
├── README.md
└── src/
    ├── main/
    │   ├── java/com/physicsfactory/
    │   │   ├── PhysicsFactoryApplication.java          # Spring Boot entry point
    │   │   ├── domain/                                 # no framework imports allowed
    │   │   │   ├── package-info.java
    │   │   │   ├── exception/
    │   │   │   │   ├── StartupValidationException.java     # base: message + remediation
    │   │   │   │   ├── BlenderNotFoundException.java
    │   │   │   │   ├── WorkspaceConfigurationException.java
    │   │   │   │   └── WorkspaceProvisioningException.java
    │   │   │   └── model/
    │   │   │       ├── WorkspaceDirectory.java             # enum: the managed folders
    │   │   │       ├── WorkspaceLayout.java                # immutable resolved paths
    │   │   │       ├── WorkspacePreparation.java
    │   │   │       ├── BlenderInstallation.java            # verified executable
    │   │   │       └── EnvironmentReport.java
    │   │   ├── application/                            # use cases + outbound ports
    │   │   │   ├── package-info.java
    │   │   │   ├── port/
    │   │   │   │   ├── DirectoryProvisioner.java
    │   │   │   │   ├── ExecutableProbe.java
    │   │   │   │   └── StartupReporter.java
    │   │   │   └── usecase/
    │   │   │       ├── BootstrapRequest.java
    │   │   │       ├── PrepareWorkspace.java
    │   │   │       ├── ValidateBlenderInstallation.java
    │   │   │       └── BootstrapEnvironment.java
    │   │   └── infrastructure/                         # the only framework-aware layer
    │   │       ├── package-info.java
    │   │       ├── bootstrap/EnvironmentBootstrapRunner.java
    │   │       ├── config/
    │   │       │   ├── PhysicsFactoryProperties.java       # typed application.yaml
    │   │       │   └── BootstrapConfiguration.java         # single wiring point
    │   │       ├── diagnostics/
    │   │       │   ├── StartupValidationFailureAnalyzer.java
    │   │       │   └── StartupExitCodeMapper.java
    │   │       ├── filesystem/
    │   │       │   ├── LocalDirectoryProvisioner.java
    │   │       │   └── LocalExecutableProbe.java
    │   │       └── logging/LoggingStartupReporter.java
    │   └── resources/
    │       ├── application.yaml
    │       ├── logback.xml
    │       └── META-INF/spring.factories
    └── test/java/com/physicsfactory/
        ├── PhysicsFactoryApplicationTests.java         # boots the app end to end
        ├── StartupValidationIntegrationTest.java       # startup must abort on a bad environment
        ├── application/usecase/…                       # use case unit tests
        ├── domain/model/WorkspaceLayoutTest.java
        ├── infrastructure/…                            # adapter and diagnostics tests
        └── support/…                                   # hand written test doubles
```

Directories created at runtime (`assets/`, `configs/`, `output/videos/`, `output/thumbnails/`,
`logs/`) are generated on first start and are git-ignored.

### Gradle wrapper

`gradle/wrapper/gradle-wrapper.properties` is committed; the binary `gradle-wrapper.jar` and the
`gradlew` scripts are not, because they cannot be produced without running Gradle. Generate them once
after cloning:

```bash
gradle wrapper --gradle-version 9.7
```

IntelliJ IDEA does not need them: it reads `gradle-wrapper.properties` and downloads the distribution
itself.

---

## Running from Gradle

```bash
# Run the application (Blender must be on PATH, or set BLENDER_EXECUTABLE)
./gradlew bootRun

# Point at a specific Blender build
BLENDER_EXECUTABLE=/Applications/Blender.app/Contents/MacOS/Blender ./gradlew bootRun

# Put the workspace somewhere other than the project directory
PHYSICS_FACTORY_HOME=/Volumes/media/physics-factory ./gradlew bootRun

# Override any property on the command line instead
./gradlew bootRun --args="--physics-factory.blender.executable-path=/usr/bin/blender"

# Build, test, and produce an executable jar in build/libs
./gradlew build
java -jar build/libs/physics-simulation-0.1.0.jar

# Tests only
./gradlew test
```

## Running from IntelliJ IDEA

1. **File → Open** and select the `physics-factory` folder. Accept the Gradle import prompt.
2. **File → Project Structure → Project**: set the SDK to a JDK 21 installation (Download JDK… →
   Temurin 21 if you do not have one).
3. **Settings → Build, Execution, Deployment → Build Tools → Gradle**: set *Gradle JVM* to JDK 21 and
   leave *Distribution* on **Gradle wrapper** ("Use Gradle from: gradle-wrapper.properties").
4. Open `PhysicsFactoryApplication` and click the green ▶ next to `main`, or use the generated Spring
   Boot run configuration. IntelliJ runs it with the project directory as the working directory, so
   the workspace folders appear in the project root.
5. Optional, if Blender is not on your `PATH`: **Run → Edit Configurations…** and add
   `BLENDER_EXECUTABLE=/path/to/blender` under *Environment variables*.
6. Right-click `src/test/java` → **Run 'Tests in physicsfactory'** to run the suite inside the IDE.

---

## Configuration

All configuration lives in `src/main/resources/application.yaml`. Nothing is hardcoded in Java, and
every value can be overridden without editing the file (command line argument, environment variable,
or `--spring.config.additional-location=file:./configs/`).

| Property                                                | Default                        | Meaning                                            |
|---------------------------------------------------------|--------------------------------|----------------------------------------------------|
| `physics-factory.workspace.root`                        | `$PHYSICS_FACTORY_HOME` or CWD | Root of every managed folder                       |
| `physics-factory.workspace.directories.assets`          | `assets`                       | Source assets                                      |
| `physics-factory.workspace.directories.configs`         | `configs`                      | Runtime scene/pipeline configuration               |
| `physics-factory.workspace.directories.video-output`     | `output/videos`                | Rendered videos                                    |
| `physics-factory.workspace.directories.thumbnail-output` | `output/thumbnails`            | Thumbnails                                         |
| `physics-factory.workspace.directories.logs`            | `logs`                         | Log files                                          |
| `physics-factory.blender.executable-path`               | `$BLENDER_EXECUTABLE` or `blender` | Absolute path, path relative to the root, or a program name resolved on `PATH` |

Directory paths must be relative to the workspace root and may not escape it; startup fails
otherwise. Logging levels are configured under `logging.level.*`; appenders live in `logback.xml`,
which writes to the console and to a rolling `logs/physics-factory.log` (20 MB per file, 14 days,
1 GB cap).

### Exit codes

| Code | Meaning                                                                    |
|------|----------------------------------------------------------------------------|
| 0    | Startup completed and the application shut down normally                   |
| 2    | Environment not ready: Blender missing, or the workspace could not be built |
| 1    | Any other failure                                                          |

A missing Blender produces a framed report rather than a stack trace:

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Blender executable 'blender' was not found or is not executable.

Action:

Install Blender and set 'physics-factory.blender.executable-path' in application.yaml (or the
BLENDER_EXECUTABLE environment variable) to the full path of the Blender binary, for example
/Applications/Blender.app/Contents/MacOS/Blender or C:\Program Files\Blender Foundation\Blender 4.2\blender.exe.
```

---

## Design decisions

**Clean Architecture in three packages, enforced by dependency direction.** `domain` holds immutable
values and invariants and imports nothing but the JDK. `application` holds use cases and the outbound
ports they need. `infrastructure` holds every Spring, Logback, filesystem and OS detail. Dependencies
point inwards only, so the parts that will grow most (rendering, AI prompt generation, uploads) can be
added as new use cases plus adapters without touching what already works. Each layer carries a
`package-info.java` stating the rule, so the constraint survives new team members.

**Use cases are plain classes; wiring is explicit.** `PrepareWorkspace`, `ValidateBlenderInstallation`
and `BootstrapEnvironment` have no annotations and take their collaborators in constructors, so they
are unit tested in microseconds without a Spring context. `BootstrapConfiguration` is the single place
where the object graph is assembled. Component scanning would have saved a few lines, but one readable
graph answers "what depends on what" instantly two years from now.

**Ports hide the awkward parts of the host machine.** Finding an executable is genuinely messy - the
value may be an absolute path, a relative path, a bare name on `PATH`, and on Windows it may need an
`.exe` suffix. All of that lives in `LocalExecutableProbe` behind `ExecutableProbe`, which keeps the
validation use case to five lines and makes the messy logic directly testable.

**The set of folders is an enum, not a list of strings.** `WorkspaceDirectory` is the single source of
truth; `application.yaml` supplies one relative path per constant, and `WorkspaceLayout.of(...)`
refuses to build a layout with a missing, blank, absolute, or root-escaping entry. Adding a folder for
a future story is one constant and one YAML line, and the two can never silently drift apart.
`WorkspaceLayout` then hands out absolute paths, so no later code needs to resolve anything itself.

**Verified values are their own types.** `BlenderInstallation` can only be created from a resolved
absolute path, so future rendering code that accepts a `BlenderInstallation` cannot be handed an
unchecked path. The same idea applies to `WorkspaceLayout` and `WorkspacePreparation`: if you hold
one, the work it represents has already been done.

**Records everywhere a value is passed around.** Configuration (`PhysicsFactoryProperties`) and domain
values are records with defensive copies of their collections, which removes an entire class of "who
mutated this?" bugs and gives correct `equals`/`toString` for free.

**Failures are domain exceptions with remediation text.** Every startup problem extends
`StartupValidationException`, which pairs a message with the action that fixes it. The framework
translates that pair into output (`StartupValidationFailureAnalyzer`, registered in
`spring.factories`) and into a process exit code (`StartupExitCodeMapper`). The domain never learns how
errors are rendered, and operators never see a stack trace for a configuration mistake.

**The workspace is provisioned before Blender is validated,** so the `logs/` directory exists and the
"Blender is missing" failure is captured in the log file rather than only on a console nobody kept.

**Bootstrapping happens in an `ApplicationRunner`, not a bean initialiser.** The lifecycle bridge is a
single class (`EnvironmentBootstrapRunner`); everything else stays lifecycle-agnostic. It also means the
integration tests exercise the real startup path.

**No `io.spring.dependency-management` plugin.** The Spring Boot BOM is imported through
`platform(SpringBootPlugin.BOM_COORDINATES)`, so the managed dependency versions always match the
plugin version and there is one less plugin to keep compatible with future Gradle releases.

**Version and name come from the build, not from code.** `springBoot { buildInfo() }` generates
`META-INF/build-info.properties`, and the startup report reads it, so the printed version can never
disagree with `build.gradle.kts`.

### Known trade-off

Spring Boot 3.5 reached open-source end of life in June 2026, and 3.5.16 is its final OSS release. The
brief specified Spring Boot 3.x, so that is what is pinned here. The upgrade to Spring Boot 4.x is a
build-file change plus a Jakarta/Spring 7 review; it is worth scheduling early rather than at the point
where a CVE forces it.

---

## Testing

```bash
./gradlew test
```

The suite is deliberately layered:

* **Domain** - `WorkspaceLayoutTest` covers path resolution, immutability, and every rejection rule.
* **Application** - use case tests drive hand written doubles (`support/`) rather than a mocking
  framework, because the assertions are about which directories are requested and in what order.
* **Infrastructure** - `LocalDirectoryProvisionerTest` and `LocalExecutableProbeTest` run against a
  real temporary filesystem; the diagnostics tests pin the operator-facing message and exit codes.
* **End to end** - `PhysicsFactoryApplicationTests` boots the application against a temporary
  workspace and a fake Blender binary and asserts the folders exist. `StartupValidationIntegrationTest`
  asserts that a missing Blender and an escaping directory path both abort startup.

---

## Extending this foundation (next stories)

* **New folder**: add a `WorkspaceDirectory` constant plus a line in `application.yaml`.
* **New configuration**: add a component to `PhysicsFactoryProperties` (or a sibling
  `@ConfigurationProperties` record) with validation annotations.
* **New capability**: add a use case in `application/usecase` with the ports it needs, implement each
  port in `infrastructure`, and register both in `BootstrapConfiguration`.
* **Rendering**: inject `WorkspaceLayout` and `BlenderInstallation`; both are guaranteed valid.
* **New failure mode**: extend `StartupValidationException` and it is automatically reported and mapped
  to exit code 2.
