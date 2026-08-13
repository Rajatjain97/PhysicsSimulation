package com.physicsfactory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.physicsfactory.domain.exception.InvalidSceneContractException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SceneContractTest {

    @Test
    void isBuiltFromARenderRequestAtTheCurrentVersion() {
        RenderRequest request = new RenderRequest("healthcheck", Path.of("output", "videos", "demo.mp4"),
                Duration.ofMinutes(2));

        SceneContract contract = SceneContract.forRequest(request);

        assertThat(contract.sceneVersion()).isEqualTo(SceneContract.CURRENT_VERSION);
        assertThat(contract.template()).isEqualTo("healthcheck");
        assertThat(contract.output().image()).isEqualTo("output/videos/demo.mp4");
        assertThat(contract.objects()).isEmpty();
    }

    @Test
    void carriesTheObjectsBlenderShouldPlace() {
        RenderRequest request = new RenderRequest("default", Path.of("output", "renders", "demo.png"),
                Duration.ofMinutes(5));

        SceneContract contract = SceneContract.forRequest(request, List.of(SceneObject.sphereAtOrigin()));

        assertThat(contract.output().image()).isEqualTo("output/renders/demo.png");
        assertThat(contract.objects()).containsExactly(new SceneObject("sphere", List.of(0.0, 0.0, 0.0)));
    }

    @Test
    void alwaysWritesForwardSlashesSoTheContractIsPortable() {
        RenderRequest request = new RenderRequest("marbles", Path.of("output").resolve("videos").resolve("a.mp4"),
                Duration.ofMinutes(1));

        assertThat(SceneContract.forRequest(request).output().image()).isEqualTo("output/videos/a.mp4");
    }

    @Test
    void rejectsDocumentsThatBreakTheContract() {
        SceneOutput output = new SceneOutput("output/videos/demo.mp4");

        assertThatThrownBy(() -> new SceneContract(0, "healthcheck", output, List.of()))
                .isInstanceOf(InvalidSceneContractException.class)
                .hasMessageContaining("sceneVersion");
        assertThatThrownBy(() -> new SceneContract(1, "  ", output, List.of()))
                .isInstanceOf(InvalidSceneContractException.class)
                .hasMessageContaining("template");
        assertThatThrownBy(() -> new SceneOutput(""))
                .isInstanceOf(InvalidSceneContractException.class)
                .hasMessageContaining("output.image");
        assertThatThrownBy(() -> new SceneObject("sphere", List.of(0.0, 0.0)))
                .isInstanceOf(InvalidSceneContractException.class)
                .hasMessageContaining("coordinates");
    }
}
