package com.physicsfactory.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.physicsfactory.domain.exception.ScriptNotFoundException;
import com.physicsfactory.domain.model.RenderRequest;
import com.physicsfactory.domain.model.RenderResult;
import com.physicsfactory.domain.model.RenderStatus;
import com.physicsfactory.domain.model.RenderWorkspace;
import com.physicsfactory.domain.model.SceneContract;
import com.physicsfactory.support.InMemoryBlenderScriptLibrary;
import com.physicsfactory.support.RecordingSceneContractWriter;
import com.physicsfactory.support.StubBlenderProcessRunner;
import com.physicsfactory.support.WorkspaceLayouts;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunBlenderHealthcheckTest {

    private static final RenderRequest REQUEST =
            new RenderRequest("healthcheck", Path.of("output/videos/healthcheck.mp4"), Duration.ofMinutes(2));

    @TempDir
    Path root;

    @Test
    void writesTheSceneContractResolvesTheScriptAndRunsIt() {
        RenderWorkspace workspace = RenderWorkspace.of(WorkspaceLayouts.rootedAt(root));
        Path script = workspace.scripts().resolve("healthcheck.py");
        InMemoryBlenderScriptLibrary library = InMemoryBlenderScriptLibrary.containing("healthcheck.py", script);
        RecordingSceneContractWriter writer = new RecordingSceneContractWriter();
        StubBlenderProcessRunner runner = StubBlenderProcessRunner.succeedingWith("blender.version=4.2.1");

        RenderResult result = new RunBlenderHealthcheck(library, writer, runner, workspace).execute(REQUEST);

        assertThat(result.status()).isEqualTo(RenderStatus.SUCCEEDED);
        assertThat(result.outputFile()).isEmpty();
        assertThat(library.lookups()).containsExactly("healthcheck.py");
        assertThat(runner.scriptRequests()).singleElement().satisfies(scriptRequest -> {
            assertThat(scriptRequest.script()).isEqualTo(script);
            assertThat(scriptRequest.timeout()).isEqualTo(REQUEST.timeout());
            assertThat(scriptRequest.arguments()).isEmpty();
        });

        Map.Entry<SceneContract, Path> write = writer.writes().get(0);
        assertThat(write.getKey()).isEqualTo(SceneContract.forRequest(REQUEST));
        // The writer is a double, so nothing is on disk; hasParent() would try to resolve it.
        assertThat(write.getValue().getParent()).isEqualTo(workspace.cache());
        assertThat(write.getValue().getFileName().toString()).endsWith(".scene.json");
    }

    @Test
    void reportsFailureWithTheExecutionAttachedInsteadOfThrowing() {
        RenderWorkspace workspace = RenderWorkspace.of(WorkspaceLayouts.rootedAt(root));
        InMemoryBlenderScriptLibrary library =
                InMemoryBlenderScriptLibrary.containing("healthcheck.py", workspace.scripts().resolve("healthcheck.py"));
        StubBlenderProcessRunner runner = StubBlenderProcessRunner.failingWith(1, "Error: no bpy module");

        RenderResult result = new RunBlenderHealthcheck(library, new RecordingSceneContractWriter(), runner, workspace)
                .execute(REQUEST);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.execution().standardError()).contains("no bpy module");
    }

    @Test
    void failsWhenTheTemplatesScriptIsNotInstalled() {
        RenderWorkspace workspace = RenderWorkspace.of(WorkspaceLayouts.rootedAt(root));
        RunBlenderHealthcheck useCase = new RunBlenderHealthcheck(InMemoryBlenderScriptLibrary.empty(),
                new RecordingSceneContractWriter(), StubBlenderProcessRunner.succeedingWith(""), workspace);

        assertThatThrownBy(() -> useCase.execute(REQUEST))
                .isInstanceOf(ScriptNotFoundException.class)
                .hasMessageContaining("healthcheck.py");
    }
}
