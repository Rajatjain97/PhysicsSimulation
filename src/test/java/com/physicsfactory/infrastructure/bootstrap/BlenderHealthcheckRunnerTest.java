package com.physicsfactory.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.physicsfactory.application.usecase.DetectBlenderVersion;
import com.physicsfactory.application.usecase.RunBlenderHealthcheck;
import com.physicsfactory.domain.exception.BlenderExecutionException;
import com.physicsfactory.domain.model.RenderRequest;
import com.physicsfactory.domain.model.RenderWorkspace;
import com.physicsfactory.support.InMemoryBlenderScriptLibrary;
import com.physicsfactory.support.RecordingSceneContractWriter;
import com.physicsfactory.support.StubBlenderProcessRunner;
import com.physicsfactory.support.WorkspaceLayouts;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

class BlenderHealthcheckRunnerTest {

    private static final RenderRequest REQUEST =
            new RenderRequest("healthcheck", Path.of("output/videos/healthcheck.mp4"), Duration.ofMinutes(2));

    @TempDir
    Path root;

    @Test
    void doesNothingWhenTheOptionIsAbsent() {
        StubBlenderProcessRunner processRunner = StubBlenderProcessRunner.succeedingWith("Blender 4.2.1");

        runner(processRunner).run(new DefaultApplicationArguments());

        assertThat(processRunner.versionProbes()).isEmpty();
        assertThat(processRunner.scriptRequests()).isEmpty();
    }

    @Test
    void detectsTheVersionAndRunsTheHealthcheckWhenAsked() {
        StubBlenderProcessRunner processRunner = StubBlenderProcessRunner.succeedingWith("Blender 4.2.1");

        runner(processRunner).run(new DefaultApplicationArguments("--" + BlenderHealthcheckRunner.HEALTHCHECK_OPTION));

        assertThat(processRunner.versionProbes()).hasSize(1);
        assertThat(processRunner.scriptRequests()).hasSize(1);
    }

    @Test
    void failsTheRunWhenBlenderReportsAProblem() {
        StubBlenderProcessRunner processRunner = StubBlenderProcessRunner.answering(
                StubBlenderProcessRunner.execution(0, "Blender 4.2.1", ""),
                StubBlenderProcessRunner.execution(1, "", "Error: no bpy module"));
        BlenderHealthcheckRunner runner = runner(processRunner);

        assertThatThrownBy(() -> runner.run(
                new DefaultApplicationArguments("--" + BlenderHealthcheckRunner.HEALTHCHECK_OPTION)))
                .isInstanceOf(BlenderExecutionException.class)
                .hasMessageContaining("exit code 1");
    }

    private BlenderHealthcheckRunner runner(StubBlenderProcessRunner processRunner) {
        RenderWorkspace workspace = RenderWorkspace.of(WorkspaceLayouts.rootedAt(root));
        RunBlenderHealthcheck healthcheck = new RunBlenderHealthcheck(
                InMemoryBlenderScriptLibrary.containing("healthcheck.py", workspace.scripts().resolve("healthcheck.py")),
                new RecordingSceneContractWriter(), processRunner, workspace);
        return new BlenderHealthcheckRunner(new DetectBlenderVersion(processRunner, Duration.ofSeconds(30)),
                healthcheck, REQUEST);
    }
}
