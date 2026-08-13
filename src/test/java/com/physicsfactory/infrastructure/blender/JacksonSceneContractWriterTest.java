package com.physicsfactory.infrastructure.blender;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.physicsfactory.domain.model.SceneContract;
import com.physicsfactory.domain.model.SceneObject;
import com.physicsfactory.domain.model.SceneOutput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JacksonSceneContractWriterTest {

    private final JacksonSceneContractWriter writer = new JacksonSceneContractWriter();

    @TempDir
    Path cache;

    @Test
    void writesExactlyTheFieldsOfTheContract() throws IOException {
        Path target = cache.resolve("job.scene.json");

        writer.write(new SceneContract(1, "default", new SceneOutput("output/renders/demo.png"),
                List.of(SceneObject.sphereAtOrigin())), target);

        JsonNode written = JsonMapper.builder().build().readTree(Files.readAllBytes(target));
        assertThat(written.size()).isEqualTo(4);
        assertThat(written.get("sceneVersion").asInt()).isEqualTo(1);
        assertThat(written.get("template").asText()).isEqualTo("default");
        assertThat(written.get("output").get("image").asText()).isEqualTo("output/renders/demo.png");
        assertThat(written.get("objects")).hasSize(1);
        assertThat(written.get("objects").get(0).get("type").asText()).isEqualTo("sphere");
        assertThat(written.get("objects").get(0).get("location").size()).isEqualTo(3);
    }

    @Test
    void createsMissingParentDirectories() {
        Path target = cache.resolve("nested").resolve("deeper").resolve("job.scene.json");

        Path written = writer.write(new SceneContract(1, "healthcheck", new SceneOutput("output/videos/demo.mp4"), List.of()), target);

        assertThat(written).isEqualTo(target);
        assertThat(target).isRegularFile();
    }

    @Test
    void replacesAnExistingContract() throws IOException {
        Path target = cache.resolve("job.scene.json");
        Files.writeString(target, "{ \"stale\": true, \"padding\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\" }");

        writer.write(new SceneContract(1, "marbles", new SceneOutput("output/videos/marbles.mp4"), List.of()),
                target);

        assertThat(Files.readString(target)).doesNotContain("stale").contains("marbles");
    }
}
