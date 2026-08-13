# Physics Reel Studio

Desktop application that procedurally generates original vertical videos for Reels, Shorts and
Spotlight. Java orchestrates; Blender renders. The application never uploads anything.

**Implemented so far: Epic 1, Stories 1.1 and 1.2 - the runtime foundation and the Blender
integration layer.**

* **Story 1.1** - on startup the application provisions its workspace, verifies the configured
  Blender installation, prints what it found, and stops with an actionable message if the environment
  is unusable.
* **Story 1.2** - Java can now run Blender: a reusable process runner, a render workspace, the scene
  JSON contract, typed failures, and a `healthcheck.py` that proves the channel works end to end.

There is deliberately no rendering, scene generation, AI generation, physics simulation or UI code
yet. Story 1.2 builds the pipe; nothing flows through it but a healthcheck.

### How Java and Blender talk

```
RenderRequest  ->  RenderJob  ->  SceneContract (JSON in blender/cache/)
                                        |
                          blender --background --python <script>
                                        |
                                  BlenderExecution  ->  RenderResult
```

Command line, filesystem, JSON, exit codes. No embedded scripting, no JNI, no Blender API in Java.
Java knows three Blender flags - `--version`, `--background`, `--python` - and nothing else about
cameras, materials, meshes, lighting or physics. All of that lives in Python, inside Blender.

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
    │   │   │   │   ├── WorkspaceProvisioningException.java
    │   │   │   │   ├── BlenderIntegrationException.java    # 1.2 base: message + remediation
    │   │   │   │   ├── BlenderExecutionException.java
    │   │   │   │   ├── BlenderTimeoutException.java
    │   │   │   │   ├── ScriptNotFoundException.java
    │   │   │   │   └── InvalidSceneContractException.java
    │   │   │   └── model/
    │   │   │       ├── WorkspaceDirectory.java             # enum: the managed folders
    │   │   │       ├── WorkspaceLayout.java                # immutable resolved paths
    │   │   │       ├── WorkspacePreparation.java
    │   │   │       ├── BlenderInstallation.java            # verified executable
    │   │   │       ├── EnvironmentReport.java
    │   │   │       ├── RenderWorkspace.java                # 1.2: the blender/ folders
    │   │   │       ├── BlenderVersion.java
    │   │   │       ├── BlenderScriptRequest.java           # what to run
    │   │   │       ├── BlenderExecution.java               # what happened
    │   │   │       ├── SceneContract.java                  # the JSON Blender reads
    │   │   │       ├── RenderRequest.java
    │   │   │       ├── RenderJobId.java
    │   │   │       ├── RenderJob.java
    │   │   │       ├── RenderStatus.java
    │   │   │       └── RenderResult.java
    │   │   ├── application/                            # use cases + outbound ports
    │   │   │   ├── package-info.java
    │   │   │   ├── port/
    │   │   │   │   ├── DirectoryProvisioner.java
    │   │   │   │   ├── ExecutableProbe.java
    │   │   │   │   ├── StartupReporter.java
    │   │   │   │   ├── BlenderProcessRunner.java           # 1.2: the only way to reach Blender
    │   │   │   │   ├── BlenderScriptLibrary.java
    │   │   │   │   └── SceneContractWriter.java
    │   │   │   └── usecase/
    │   │   │       ├── BootstrapRequest.java
    │   │   │       ├── PrepareWorkspace.java
    │   │   │       ├── ValidateBlenderInstallation.java
    │   │   │       ├── BootstrapEnvironment.java
    │   │   │       ├── DetectBlenderVersion.java
    │   │   │       └── RunBlenderHealthcheck.java
    │   │   └── infrastructure/                         # the only framework-aware layer
    │   │       ├── package-info.java
    │   │       ├── blender/                                # 1.2: the Java/Blender boundary
    │   │       │   ├── ProcessBlenderRunner.java
    │   │       │   ├── ClasspathBlenderScriptLibrary.java
    │   │       │   └── JacksonSceneContractWriter.java
    │   │       ├── bootstrap/
    │   │       │   ├── EnvironmentBootstrapRunner.java
    │   │       │   └── BlenderHealthcheckRunner.java       # --healthcheck
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
    │       ├── META-INF/spring.factories
    │       └── blender/scripts/
    │           └── healthcheck.py                      # installed into the workspace on startup
    └── test/java/com/physicsfactory/
        ├── PhysicsFactoryApplicationTests.java         # boots the app end to end
        ├── StartupValidationIntegrationTest.java       # startup must abort on a bad environment
        ├── BlenderHealthcheckIntegrationTest.java      # runs healthcheck.py in real Blender
        ├── application/usecase/…                       # use case unit tests
        ├── domain/model/…                              # contract and invariant tests
        ├── infrastructure/…                            # adapter and diagnostics tests
        └── support/…                                   # hand written test doubles
```

Directories created at runtime are generated on first start and are git-ignored:

```
assets/  configs/  logs/           deliverables and inputs
output/videos/  output/thumbnails/ what the user came for
blender/scripts/                   Python installed from the jar
blender/templates/                 .blend templates
blender/renders/                   Blender's own output
blender/cache/                     scene contracts and scratch files
```

Everything under `blender/` is the render engine's working area and is safe to delete; everything
under `output/` is a deliverable. They are kept apart on purpose.

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
7. To run the healthcheck from the IDE, add `--healthcheck` to *Program arguments* in the run
   configuration.

---

## Running the healthcheck

The healthcheck answers one question: **can this machine render?** It detects the Blender version,
then runs `blender/scripts/healthcheck.py` inside Blender and reports what came back.

```bash
# Against Blender on the PATH
./gradlew bootRun --args="--healthcheck"

# Against a specific Blender build
BLENDER_EXECUTABLE=/Applications/Blender.app/Contents/MacOS/Blender ./gradlew bootRun --args="--healthcheck"

# From the packaged jar - this is the form to use in CI
java -jar build/libs/physics-simulation-0.1.0.jar --healthcheck
echo $?   # 0 = Blender works, 2 = Blender not found, 3 = Blender ran but failed
```

Expected output:

```
Blender version detected: 4.2.1 (Blender 4.2.1)
Blender script healthcheck.py starting | executable=/usr/bin/blender | arguments=[--background, --python, …] | timeout=PT2M
Blender script healthcheck.py succeeded | exitCode=0 | duration=1483ms
Blender healthcheck SUCCEEDED in 1483ms
Blender reported:
blender.version=4.2.1
python.version=3.11.7
working.directory=/Users/you/physics-reel-studio
```

The same path is covered by `BlenderHealthcheckIntegrationTest`, which is skipped automatically when
Blender is not installed:

```bash
./gradlew test --tests "*BlenderHealthcheckIntegrationTest"
```

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
| `physics-factory.workspace.directories.blender-scripts`  | `blender/scripts`              | Python installed from the jar                      |
| `physics-factory.workspace.directories.blender-templates`| `blender/templates`            | `.blend` scene templates                           |
| `physics-factory.workspace.directories.blender-renders`  | `blender/renders`              | Blender's own render output                        |
| `physics-factory.workspace.directories.blender-cache`    | `blender/cache`                | Scene contracts and scratch files                  |
| `physics-factory.blender.executable-path`               | `$BLENDER_EXECUTABLE` or `blender` | Absolute path, path relative to the root, or a program name resolved on `PATH` |
| `physics-factory.blender.version-timeout`               | `30s`                          | Budget for `blender --version`                     |
| `physics-factory.blender.healthcheck.template`          | `healthcheck`                  | Template name; also names the script (`healthcheck.py`) |
| `physics-factory.blender.healthcheck.output-file`       | `output/videos/healthcheck.mp4`| Where the template *would* write; the healthcheck renders nothing |
| `physics-factory.blender.healthcheck.timeout`           | `2m`                           | Budget for the healthcheck run                     |

Directory paths must be relative to the workspace root and may not escape it; startup fails
otherwise. Logging levels are configured under `logging.level.*`; appenders live in `logback.xml`,
which writes to the console and to a rolling `logs/physics-factory.log` (20 MB per file, 14 days,
1 GB cap).

### Exit codes

| Code | Meaning                                                                    |
|------|----------------------------------------------------------------------------|
| 0    | Startup completed and the application shut down normally                   |
| 2    | Environment not ready: Blender missing, or the workspace could not be built |
| 3    | Blender was reachable but the invocation failed, timed out, or produced nothing usable |
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

**The workspace is provisioned, then the scripts are installed, then Blender is validated.** The
`logs/` directory exists before anything can fail, and a machine without Blender still ends up with a
fully prepared workspace.

**Bootstrapping happens in an `ApplicationRunner`, not a bean initialiser.** The lifecycle bridge is a
single class (`EnvironmentBootstrapRunner`); everything else stays lifecycle-agnostic. It also means the
integration tests exercise the real startup path.

**No `io.spring.dependency-management` plugin.** The Spring Boot BOM is imported through
`platform(SpringBootPlugin.BOM_COORDINATES)`, so the managed dependency versions always match the
plugin version and there is one less plugin to keep compatible with future Gradle releases.

**Version and name come from the build, not from code.** `springBoot { buildInfo() }` generates
`META-INF/build-info.properties`, and the startup report reads it, so the printed version can never
disagree with `build.gradle.kts`.

### Story 1.2: the Blender integration layer

**Story 1.2 added no new architecture.** Every piece landed in a slot Story 1.1 already had: four
constants on `WorkspaceDirectory` for the render folders, three new ports, two new use cases, one new
infrastructure package, and beans in the existing `BootstrapConfiguration`. That is the test of
whether the foundation was right, and it held.

**`ProcessBlenderRunner` is the entire Java/Blender boundary.** One class knows three flags -
`--version`, `--background`, `--python` - and the `--` separator. Nothing else in Java has any idea
what Blender is. When a future story needs GPU selection or `--factory-startup`, exactly one file
changes.

**Blender's executable is re-validated on every invocation, not resolved once at startup.** Blender
lives outside our control and a desktop application can run for days; caching the path would mean
failing confusingly long after somebody upgraded or moved it. The check is a filesystem stat, and
reusing `ValidateBlenderInstallation` keeps one definition of "usable Blender" in the codebase.

**Both output streams are drained on virtual threads.** Blender is chatty. Reading stdout on the
calling thread would let the pipe fill and deadlock before the timeout was ever evaluated - the
classic `ProcessBuilder` bug. On timeout the whole process tree is destroyed (`descendants()`), and
draining is given a bounded grace period so a surviving grandchild cannot hang the caller.

**A non-zero exit code is a result, not an exception.** `runScript` returns the full
`BlenderExecution` including stderr, and the use case decides what that means. Exceptions are
reserved for "there is nothing to hand back": the process would not start, the timeout expired, the
script is missing. This is what lets a future batch renderer record a failed job and carry on instead
of unwinding the batch.

**Blender runs with the workspace root as its working directory.** That single choice is what allows
every path in a scene contract to be relative and forward-slash separated: the same JSON means the
same thing on macOS, Linux and Windows, and a workspace can be moved or shared without rewriting it.

**The scene contract is versioned from day one.** `sceneVersion` is written on every document, so a
Blender script can refuse input it does not understand rather than guess. Fields get added, never
renamed or removed; a breaking change means a new `CURRENT_VERSION`. The writer configures its own
`ObjectMapper` instead of injecting the application-wide one, so nobody can change the wire format by
tuning a shared bean.

**Python ships inside the jar and is installed into the workspace.** `blender/scripts` is populated
from the classpath on every start, replacing edited copies. The Java and the Python that talk to each
other are versioned together and cannot drift, Blender always gets a real file it can read, and an
operator can still look at exactly what ran. Adding a script for a future template is one file in
`src/main/resources/blender/scripts`.

**Template names are validated, not trusted.** A template name becomes a script file name, so
`RenderRequest` restricts it to `[a-z0-9-_]` and `BlenderScriptLibrary.locate` rejects anything whose
parent is not the scripts directory. A configured template can never reach outside the workspace.

**Blender failures are their own exception family.** `BlenderIntegrationException` sits beside
`StartupValidationException` rather than under it: one means "this environment cannot start", the
other means "this invocation failed". Both carry remediation text, and both map to their own exit
code, so a CI job can tell "install Blender" apart from "the render broke".

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

* **Domain** - `WorkspaceLayoutTest` covers path resolution and every rejection rule;
  `SceneContractTest`, `RenderRequestTest`, `BlenderVersionTest` and `BlenderExecutionTest` pin the
  contracts, including forward-slash output paths and template names that cannot escape the scripts
  directory.
* **Application** - use case tests drive hand written doubles (`support/`) rather than a mocking
  framework, because the assertions are about which script was located, what was written where, and
  in what order.
* **Infrastructure** - the adapter tests run against a real temporary filesystem.
  `ProcessBlenderRunnerTest` is the interesting one: it points the runner at a **stand-in Blender
  shell script**, which covers stdout and stderr capture, exit codes, argument passing and the
  timeout kill path without needing Blender installed. `ClasspathBlenderScriptLibraryTest` installs
  the real bundled `healthcheck.py` from the classpath.
* **End to end** - `PhysicsFactoryApplicationTests` boots the application against a temporary
  workspace and a fake Blender binary and asserts the folders and scripts exist.
  `StartupValidationIntegrationTest` asserts that a missing Blender and an escaping directory path
  both abort startup.
* **Integration with real Blender** - `BlenderHealthcheckIntegrationTest` boots the application
  against the Blender actually installed on the machine and runs `healthcheck.py` through the whole
  chain. It is **skipped, not failed**, when Blender is absent: the condition is evaluated before the
  Spring context is built, so CI without Blender stays green while a developer's machine gets real
  coverage.

---

## Extension points (deliberately not implemented)

These are the seams the next stories are expected to use. None of them exist as speculative code
today; each is a place where the current design already bends.

**The first real template.** Add `marbles.py` to `src/main/resources/blender/scripts` and a
`RenderTemplate` bean or config entry naming it. `RunBlenderHealthcheck` shows the sequence a
`RenderVideo` use case will follow - accept a request, create a job, write the scene contract, locate
the script, run it, map the result. The difference will be the template being executed and the
`RenderResult` carrying an output file, not the shape of the flow.

**Passing the scene contract to Blender.** The contract is already written to `blender/cache`. When a
template needs to read it, pass its path through `BlenderScriptRequest.arguments()`: the runner
already appends `--` and forwards everything after it to Python. The healthcheck deliberately takes
no arguments, so nothing unused ships today.

**Scene definition (physics, camera presets, lighting presets, material library, story engine, text
overlays, audio).** All of these are fields on the scene contract, not Java classes. Add them to
`SceneContract` behind a bumped `CURRENT_VERSION`, and read them in Python. Java gains a data field;
it still knows nothing about Blender.

**MP4 rendering.** `RenderRequest.outputFile` and `RenderResult.outputFile` already exist for it.
Blender writes to the path in the contract, resolved against the workspace root; Java asserts the
file appeared and returns `RenderResult.succeeded(jobId, execution, outputFile)`.

**Batch generation.** `RenderJobId` and `RenderJob` are the identity a queue needs. A batch runner
sits above `RunBlenderHealthcheck`'s successor, catching `BlenderTimeoutException` per job. That is
also when `RenderStatus` grows `PENDING`, `RUNNING` and `TIMED_OUT` - constants that would be dead
today.

**Blender version gates.** `DetectBlenderVersion` returns a structured `BlenderVersion`. A template
that needs Blender 4.2 compares against it before running, instead of failing halfway through.

**Progress reporting and a UI.** `StartupReporter` is already a port with a logging adapter; a desktop
UI adds a second implementation. Long-running renders will want a `RenderProgressReporter` port
alongside it, fed by parsing Blender's stdout in `ProcessBlenderRunner`.

**Mechanics that have not changed since Story 1.1**

* **New folder**: add a `WorkspaceDirectory` constant plus a line in `application.yaml`.
* **New configuration**: add a component to `PhysicsFactoryProperties` with validation annotations.
* **New capability**: add a use case in `application/usecase` with the ports it needs, implement each
  port in `infrastructure`, and register both in `BootstrapConfiguration`.
* **New startup failure**: extend `StartupValidationException` - reported and mapped to exit code 2
  automatically.
* **New Blender failure**: extend `BlenderIntegrationException` - mapped to exit code 3 automatically.
