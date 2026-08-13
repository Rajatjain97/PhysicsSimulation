package com.physicsfactory.infrastructure.blender;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.physicsfactory.domain.model.SceneContract;
import com.physicsfactory.domain.model.SceneOutput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JacksonSceneContractWriterTest {

    private final JacksonSceneContractWriter writer = new JacksonSceneContractWriter();

    @TempDir
    Path cache;

    @Test
    void writesExactlyTheFieldsOfTheContract() throws IOException {
        Path target = cache.resolve("job.scene.json");

        writer.write(new SceneContract("1.0", "DefaultSphere", Map.of("background", "white"),
                new SceneOutput("output/renders/demo.png")), target);

        JsonNode written = JsonMapper.builder().build().readTree(Files.readAllBytes(target));
        assertThat(written.size()).isEqualTo(4);
        assertThat(written.get("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(written.get("template").asText()).isEqualTo("DefaultSphere");
        assertThat(written.get("parameters").get("background").asText()).isEqualTo("white");
        assertThat(written.get("output").get("image").asText()).isEqualTo("output/renders/demo.png");
    }

    @Test
    void createsMissingParentDirectories() {
        Path target = cache.resolve("nested").resolve("deeper").resolve("job.scene.json");

        Path written = writer.write(new SceneContract("1.0", "DefaultSphere", Map.of(),
                new SceneOutput("output/renders/demo.png")), target);

        assertThat(written).isEqualTo(target);
        assertThat(target).isRegularFile();
    }

    @Test
    void replacesAnExistingContract() throws IOException {
        Path target = cache.resolve("job.scene.json");
        Files.writeString(target, "{ \"stale\": true, \"padding\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\" }");

        writer.write(new SceneContract("1.0", "MarbleArena", Map.of(),
                new SceneOutput("output/renders/marbles.png")), target);

        assertThat(Files.readString(target)).doesNotContain("stale").contains("MarbleArena");
    }
}
