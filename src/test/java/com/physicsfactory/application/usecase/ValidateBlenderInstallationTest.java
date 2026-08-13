package com.physicsfactory.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.physicsfactory.domain.exception.BlenderNotFoundException;
import com.physicsfactory.domain.model.BlenderInstallation;
import com.physicsfactory.support.FixedExecutableProbe;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidateBlenderInstallationTest {

    @TempDir
    Path directory;

    @Test
    void returnsAVerifiedInstallationWhenTheProbeResolvesTheExecutable() {
        Path resolved = directory.resolve("blender").toAbsolutePath();
        ValidateBlenderInstallation useCase =
                new ValidateBlenderInstallation(FixedExecutableProbe.resolving("blender", resolved));

        BlenderInstallation installation = useCase.execute("blender");

        assertThat(installation.executable()).isEqualTo(resolved);
    }

    @Test
    void failsWithRemediationAdviceWhenNothingIsFound() {
        ValidateBlenderInstallation useCase =
                new ValidateBlenderInstallation(FixedExecutableProbe.resolvingNothing());

        Throwable thrown = catchThrowable(() -> useCase.execute("/opt/blender/blender"));

        assertThat(thrown)
                .isInstanceOf(BlenderNotFoundException.class)
                .hasMessageContaining("/opt/blender/blender");
        BlenderNotFoundException failure = (BlenderNotFoundException) thrown;
        assertThat(failure.configuredLocation()).isEqualTo("/opt/blender/blender");
        assertThat(failure.remediation()).contains("physics-factory.blender.executable-path");
    }

    @Test
    void failsWhenNothingIsConfiguredWithoutTouchingTheProbe() {
        FixedExecutableProbe probe = FixedExecutableProbe.resolvingNothing();
        ValidateBlenderInstallation useCase = new ValidateBlenderInstallation(probe);

        assertThatThrownBy(() -> useCase.execute("   ")).isInstanceOf(BlenderNotFoundException.class);
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(BlenderNotFoundException.class);
        assertThat(probe.requests()).isEmpty();
    }
}
