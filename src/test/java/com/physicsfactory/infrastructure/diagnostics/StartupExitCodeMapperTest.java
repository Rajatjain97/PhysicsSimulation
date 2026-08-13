package com.physicsfactory.infrastructure.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.physicsfactory.domain.exception.BlenderNotFoundException;
import com.physicsfactory.domain.exception.ScriptNotFoundException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StartupExitCodeMapperTest {

    private final StartupExitCodeMapper mapper = new StartupExitCodeMapper();

    @Test
    void mapsStartupValidationFailuresToADedicatedExitCode() {
        assertThat(mapper.getExitCode(new BlenderNotFoundException("blender")))
                .isEqualTo(StartupExitCodeMapper.ENVIRONMENT_NOT_READY);
    }

    @Test
    void unwrapsTheExceptionSpringBootWrapsRunnerFailuresIn() {
        Throwable wrapped = new IllegalStateException("Failed to execute ApplicationRunner",
                new BlenderNotFoundException("blender"));

        assertThat(mapper.getExitCode(wrapped)).isEqualTo(StartupExitCodeMapper.ENVIRONMENT_NOT_READY);
    }

    @Test
    void mapsBlenderIntegrationFailuresToTheirOwnExitCode() {
        assertThat(mapper.getExitCode(new ScriptNotFoundException("healthcheck.py", Path.of("blender/scripts"))))
                .isEqualTo(StartupExitCodeMapper.BLENDER_INTEGRATION_FAILURE);
        assertThat(mapper.getExitCode(new IllegalStateException("Failed to execute ApplicationRunner",
                new ScriptNotFoundException("healthcheck.py", Path.of("blender/scripts")))))
                .isEqualTo(StartupExitCodeMapper.BLENDER_INTEGRATION_FAILURE);
    }

    @Test
    void mapsEverythingElseToTheGenericFailureCode() {
        assertThat(mapper.getExitCode(new RuntimeException("boom")))
                .isEqualTo(StartupExitCodeMapper.UNEXPECTED_FAILURE);
    }

    @Test
    void inspectsTheWholeCauseChain() {
        Throwable failure = new BlenderNotFoundException("blender");
        for (int i = 0; i < 100; i++) {
            failure = new IllegalStateException("layer " + i, failure);
        }

        assertThat(mapper.getExitCode(failure)).isEqualTo(StartupExitCodeMapper.ENVIRONMENT_NOT_READY);
    }
}
