package com.physicsfactory.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.physicsfactory.domain.exception.BlenderExecutionException;
import com.physicsfactory.domain.model.BlenderVersion;
import com.physicsfactory.support.StubBlenderProcessRunner;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DetectBlenderVersionTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    void readsTheVersionFromTheProbeOutput() {
        StubBlenderProcessRunner runner = StubBlenderProcessRunner.succeedingWith("Blender 4.2.1 LTS\n");

        BlenderVersion version = new DetectBlenderVersion(runner, TIMEOUT).execute();

        assertThat(version.shortVersion()).isEqualTo("4.2.1");
        assertThat(runner.versionProbes()).containsExactly(TIMEOUT);
    }

    @Test
    void failsWhenTheProbeItselfFailed() {
        StubBlenderProcessRunner runner = StubBlenderProcessRunner.failingWith(127, "blender: not found");

        assertThatThrownBy(() -> new DetectBlenderVersion(runner, TIMEOUT).execute())
                .isInstanceOf(BlenderExecutionException.class)
                .hasMessageContaining("127");
    }

    @Test
    void failsWhenTheExecutableIsNotBlender() {
        StubBlenderProcessRunner runner = StubBlenderProcessRunner.succeedingWith("GNU bash, version 5.2.15\n");

        assertThatThrownBy(() -> new DetectBlenderVersion(runner, TIMEOUT).execute())
                .isInstanceOf(BlenderExecutionException.class)
                .hasMessageContaining("Could not read a Blender version");
    }
}
