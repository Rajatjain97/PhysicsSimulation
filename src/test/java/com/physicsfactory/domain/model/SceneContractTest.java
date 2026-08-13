package com.physicsfactory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.physicsfactory.domain.exception.InvalidSceneContractException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SceneContractTest {

    @Test
    void isBuiltFromARenderRequestAtTheCurrentVersion() {
        RenderRequest request = new RenderRequest("DefaultSphere", Path.of("output", "renders", "demo.png"),
                Duration.ofMinutes(5));

        SceneContract contract = SceneContract.forRequest(request);

        assertThat(contract.schemaVersion()).isEqualTo(SceneContract.CURRENT_VERSION);
        assertThat(contract.template()).isEqualTo("DefaultSphere");
        assertThat(contract.output().image()).isEqualTo("output/renders/demo.png");
        assertThat(contract.parameters()).isEmpty();
    }

    @Test
    void alwaysWritesForwardSlashesSoTheContractIsPortable() {
        RenderRequest request = new RenderRequest("DefaultSphere",
                Path.of("output").resolve("renders").resolve("a.png"), Duration.ofMinutes(1));

        assertThat(SceneContract.forRequest(request).output().image()).isEqualTo("output/renders/a.png");
    }

    @Test
    void carriesTemplateParametersWithoutInterpretingThem() {
        SceneContract contract = new SceneContract("1.0", "DefaultSphere", Map.of("background", "white"),
                new SceneOutput("output/renders/demo.png"));

        assertThat(contract.parameters()).containsEntry("background", "white");
        assertThatThrownBy(() -> contract.parameters().put("tint", "red"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDocumentsThatBreakTheContract() {
        SceneOutput output = new SceneOutput("output/renders/demo.png");

        assertThatThrownBy(() -> new SceneContract(" ", "DefaultSphere", Map.of(), output))
                .isInstanceOf(InvalidSceneContractException.class)
                .hasMessageContaining("schemaVersion");
        assertThatThrownBy(() -> new SceneContract("1.0", "  ", Map.of(), output))
                .isInstanceOf(InvalidSceneContractException.class)
                .hasMessageContaining("template");
        assertThatThrownBy(() -> new SceneOutput(""))
                .isInstanceOf(InvalidSceneContractException.class)
                .hasMessageContaining("output.image");
    }
}
