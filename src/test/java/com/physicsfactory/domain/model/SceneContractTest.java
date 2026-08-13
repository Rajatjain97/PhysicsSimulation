package com.physicsfactory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.physicsfactory.domain.exception.InvalidSceneContractException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SceneContractTest {

    @Test
    void isBuiltFromARenderRequestAtTheCurrentVersion() {
        RenderRequest request = new RenderRequest("healthcheck", Path.of("output", "videos", "demo.mp4"),
                Duration.ofMinutes(2));

        SceneContract contract = SceneContract.forRequest(request);

        assertThat(contract.sceneVersion()).isEqualTo(SceneContract.CURRENT_VERSION);
        assertThat(contract.template()).isEqualTo("healthcheck");
        assertThat(contract.output()).isEqualTo("output/videos/demo.mp4");
    }

    @Test
    void alwaysWritesForwardSlashesSoTheContractIsPortable() {
        RenderRequest request = new RenderRequest("marbles", Path.of("output").resolve("videos").resolve("a.mp4"),
                Duration.ofMinutes(1));

        assertThat(SceneContract.forRequest(request).output()).isEqualTo("output/videos/a.mp4");
    }

    @Test
    void rejectsDocumentsThatBreakTheContract() {
        assertThatThrownBy(() -> new SceneContract(0, "healthcheck", "output/videos/demo.mp4"))
                .isInstanceOf(InvalidSceneContractException.class)
                .hasMessageContaining("sceneVersion");
        assertThatThrownBy(() -> new SceneContract(1, "  ", "output/videos/demo.mp4"))
                .isInstanceOf(InvalidSceneContractException.class)
                .hasMessageContaining("template");
        assertThatThrownBy(() -> new SceneContract(1, "healthcheck", ""))
                .isInstanceOf(InvalidSceneContractException.class)
                .hasMessageContaining("output");
    }
}
