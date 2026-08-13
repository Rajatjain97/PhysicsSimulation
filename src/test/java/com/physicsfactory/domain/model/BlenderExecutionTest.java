package com.physicsfactory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlenderExecutionTest {

    @Test
    void treatsExitCodeZeroAsSuccess() {
        assertThat(execution(0).isSuccessful()).isTrue();
        assertThat(execution(1).isSuccessful()).isFalse();
    }

    @Test
    void rendersTheCommandLineForLogsAndErrorMessages() {
        BlenderExecution execution = new BlenderExecution(List.of("/usr/bin/blender", "--version"), 0, "", "",
                Duration.ofMillis(10));

        assertThat(execution.commandLine()).isEqualTo("/usr/bin/blender --version");
    }

    @Test
    void copiesTheCommandSoItCannotChangeAfterwards() {
        List<String> mutableCommand = new ArrayList<>(List.of("blender", "--version"));
        BlenderExecution execution = new BlenderExecution(mutableCommand, 0, "", "", Duration.ZERO);

        mutableCommand.add("--factory-startup");

        assertThat(execution.command()).containsExactly("blender", "--version");
        assertThatThrownBy(() -> execution.command().add("--quiet"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsAnEmptyCommandOrNegativeDuration() {
        assertThatThrownBy(() -> new BlenderExecution(List.of(), 0, "", "", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlenderExecution(List.of("blender"), 0, "", "", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BlenderExecution execution(int exitCode) {
        return new BlenderExecution(List.of("blender"), exitCode, "", "", Duration.ofMillis(5));
    }
}
