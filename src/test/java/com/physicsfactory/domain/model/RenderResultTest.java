package com.physicsfactory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderResultTest {

    private static final RenderJobId JOB_ID = RenderJobId.newId();
    private static final BlenderExecution SUCCESS =
            new BlenderExecution(List.of("blender"), 0, "blender.version=4.2.1", "", Duration.ofMillis(200));
    private static final BlenderExecution FAILURE =
            new BlenderExecution(List.of("blender"), 1, "", "Error: script failed", Duration.ofMillis(150));

    @Test
    void reportsSuccessWithoutAFileForJobsThatRenderNothing() {
        RenderResult result = RenderResult.succeeded(JOB_ID, SUCCESS);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.status()).isEqualTo(RenderStatus.SUCCEEDED);
        assertThat(result.outputFile()).isEmpty();
        assertThat(result.execution()).isEqualTo(SUCCESS);
    }

    @Test
    void carriesTheRenderedFileWhenThereIsOne() {
        Path video = Path.of("output/videos/demo.mp4");

        RenderResult result = RenderResult.succeeded(JOB_ID, SUCCESS, video);

        assertThat(result.outputFile()).contains(video);
    }

    @Test
    void keepsTheExecutionOnFailureSoTheCallerCanReadStderr() {
        RenderResult result = RenderResult.failed(JOB_ID, FAILURE);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.status()).isEqualTo(RenderStatus.FAILED);
        assertThat(result.execution().standardError()).contains("script failed");
    }
}
