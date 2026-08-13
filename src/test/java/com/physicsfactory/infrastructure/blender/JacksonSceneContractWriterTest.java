package com.physicsfactory.infrastructure.blender;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.physicsfactory.domain.model.SceneContract;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JacksonSceneContractWriterTest {

    private final JacksonSceneContractWriter writer = new JacksonSceneContractWriter();

    @TempDir
    Path cache;

    @Test
    void writesExactlyTheThreeFieldsOfTheContract() throws IOException {
        Path target = cache.resolve("job.scene.json");

        writer.write(new SceneContract(1, "healthcheck", "output/videos/demo.mp4"), target);

        JsonNode written = JsonMapper.builder().build().readTree(Files.readAllBytes(target));
        assertThat(written.size()).isEqualTo(3);
        assertThat(written.get("sceneVersion").asInt()).isEqualTo(1);
        assertThat(written.get("template").asText()).isEqualTo("healthcheck");
        assertThat(written.get("output").asText()).isEqualTo("output/videos/demo.mp4");
    }

    @Test
    void createsMissingParentDirectories() {
        Path target = cache.resolve("nested").resolve("deeper").resolve("job.scene.json");

        Path written = writer.write(new SceneContract(1, "healthcheck", "output/videos/demo.mp4"), target);

        assertThat(written).isEqualTo(target);
        assertThat(target).isRegularFile();
    }

    @Test
    void replacesAnExistingContract() throws IOException {
        Path target = cache.resolve("job.scene.json");
        Files.writeString(target, "{ \"stale\": true, \"padding\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\" }");

        writer.write(new SceneContract(1, "marbles", "output/videos/marbles.mp4"), target);

        assertThat(Files.readString(target)).doesNotContain("stale").contains("marbles");
    }
}
