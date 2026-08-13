package com.physicsfactory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.physicsfactory.domain.exception.InvalidSceneContractException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RenderRequestTest {

    private static final Path OUTPUT = Path.of("output/videos/demo.mp4");
    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    @Test
    void derivesTheScriptNameFromTheTemplate() {
        assertThat(new RenderRequest("healthcheck", OUTPUT, TIMEOUT).scriptName()).isEqualTo("healthcheck.py");
        assertThat(new RenderRequest("glass-marbles", OUTPUT, TIMEOUT).scriptName()).isEqualTo("glass-marbles.py");
    }

    @Test
    void rejectsTemplateNamesThatCouldEscapeTheScriptDirectory() {
        assertThatThrownBy(() -> new RenderRequest("../../etc/passwd", OUTPUT, TIMEOUT))
                .isInstanceOf(InvalidSceneContractException.class);
        assertThatThrownBy(() -> new RenderRequest("blender/healthcheck", OUTPUT, TIMEOUT))
                .isInstanceOf(InvalidSceneContractException.class);
        assertThatThrownBy(() -> new RenderRequest("Healthcheck", OUTPUT, TIMEOUT))
                .isInstanceOf(InvalidSceneContractException.class);
        assertThatThrownBy(() -> new RenderRequest("", OUTPUT, TIMEOUT))
                .isInstanceOf(InvalidSceneContractException.class);
    }

    @Test
    void requiresAnOutputPathInsideTheWorkspace() {
        assertThatThrownBy(() -> new RenderRequest("healthcheck", Path.of("/tmp/demo.mp4").toAbsolutePath(), TIMEOUT))
                .isInstanceOf(InvalidSceneContractException.class)
                .hasMessageContaining("relative");
        assertThatThrownBy(() -> new RenderRequest("healthcheck", Path.of("../demo.mp4"), TIMEOUT))
                .isInstanceOf(InvalidSceneContractException.class)
                .hasMessageContaining("inside the workspace");
    }

    @Test
    void requiresAPositiveTimeout() {
        assertThatThrownBy(() -> new RenderRequest("healthcheck", OUTPUT, Duration.ZERO))
                .isInstanceOf(InvalidSceneContractException.class);
        assertThatThrownBy(() -> new RenderRequest("healthcheck", OUTPUT, Duration.ofSeconds(-1)))
                .isInstanceOf(InvalidSceneContractException.class);
    }
}
