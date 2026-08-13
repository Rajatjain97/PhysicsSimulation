package com.physicsfactory.infrastructure.blender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.physicsfactory.application.usecase.ValidateBlenderInstallation;
import com.physicsfactory.domain.exception.BlenderNotFoundException;
import com.physicsfactory.domain.exception.BlenderTimeoutException;
import com.physicsfactory.domain.exception.ScriptNotFoundException;
import com.physicsfactory.domain.model.BlenderExecution;
import com.physicsfactory.domain.model.BlenderScriptRequest;
import com.physicsfactory.support.FixedExecutableProbe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the real process machinery against a stand-in "Blender" shell script, so stream capture,
 * exit codes, timeouts and argument passing are covered without needing Blender installed.
 *
 * <p>POSIX only: the stand-in is a shell script. The behaviour under test is
 * {@link ProcessBuilder}'s, which is the same on Windows.
 */
@EnabledOnOs({OS.LINUX, OS.MAC})
class ProcessBlenderRunnerTest {

    private static final String CONFIGURED_BLENDER = "blender";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @TempDir
    Path workingDirectory;

    @Test
    void capturesStandardOutputStandardErrorExitCodeAndDuration() throws IOException {
        ProcessBlenderRunner runner = runnerFor("""
                #!/bin/sh
                echo "Blender 4.2.1 LTS"
                echo "warning: no GPU found" >&2
                exit 0
                """);

        BlenderExecution execution = runner.runVersionProbe(TIMEOUT);

        assertThat(execution.exitCode()).isZero();
        assertThat(execution.isSuccessful()).isTrue();
        assertThat(execution.standardOutput()).contains("Blender 4.2.1 LTS");
        assertThat(execution.standardError()).contains("no GPU found");
        assertThat(execution.duration()).isPositive();
        assertThat(execution.command()).endsWith("--version");
    }

    @Test
    void reportsANonZeroExitCodeAsAResultRatherThanAnException() throws IOException {
        ProcessBlenderRunner runner = runnerFor("""
                #!/bin/sh
                echo "Error: cannot open scene" >&2
                exit 42
                """);

        BlenderExecution execution = runner.runVersionProbe(TIMEOUT);

        assertThat(execution.exitCode()).isEqualTo(42);
        assertThat(execution.isSuccessful()).isFalse();
        assertThat(execution.standardError()).contains("cannot open scene");
    }

    @Test
    void runsAScriptInBackgroundModeAndForwardsItsArguments() throws IOException {
        ProcessBlenderRunner runner = runnerFor("""
                #!/bin/sh
                echo "received:$*"
                """);
        Path script = Files.writeString(workingDirectory.resolve("healthcheck.py"), "print('hi')");

        BlenderExecution execution = runner.runScript(
                new BlenderScriptRequest(script, List.of("--scene", "demo.json"), TIMEOUT));

        assertThat(execution.standardOutput())
                .contains("--background")
                .contains("--python " + script)
                .contains("-- --scene demo.json");
    }

    @Test
    void killsBlenderWhenItOutlivesItsTimeout() throws IOException {
        ProcessBlenderRunner runner = runnerFor("""
                #!/bin/sh
                sleep 10
                """);
        Path script = Files.writeString(workingDirectory.resolve("slow.py"), "print('slow')");

        assertThatThrownBy(() -> runner.runScript(BlenderScriptRequest.of(script, Duration.ofMillis(300))))
                .isInstanceOf(BlenderTimeoutException.class)
                .hasMessageContaining("PT0.3S");
    }

    @Test
    void refusesToRunAScriptThatDoesNotExist() throws IOException {
        ProcessBlenderRunner runner = runnerFor("""
                #!/bin/sh
                exit 0
                """);

        assertThatThrownBy(() -> runner.runScript(
                BlenderScriptRequest.of(workingDirectory.resolve("missing.py"), TIMEOUT)))
                .isInstanceOf(ScriptNotFoundException.class)
                .hasMessageContaining("missing.py");
    }

    @Test
    void failsWithTheStory11ErrorWhenBlenderCannotBeResolved() {
        ProcessBlenderRunner runner = new ProcessBlenderRunner(
                new ValidateBlenderInstallation(FixedExecutableProbe.resolvingNothing()),
                CONFIGURED_BLENDER, workingDirectory);

        assertThatThrownBy(() -> runner.runVersionProbe(TIMEOUT)).isInstanceOf(BlenderNotFoundException.class);
    }

    private ProcessBlenderRunner runnerFor(String shellScript) throws IOException {
        Path fakeBlender = workingDirectory.resolve("fake-blender");
        Files.writeString(fakeBlender, shellScript);
        Files.setPosixFilePermissions(fakeBlender, PosixFilePermissions.fromString("rwxr-xr-x"));
        return new ProcessBlenderRunner(
                new ValidateBlenderInstallation(FixedExecutableProbe.resolving(CONFIGURED_BLENDER, fakeBlender)),
                CONFIGURED_BLENDER, workingDirectory);
    }
}
