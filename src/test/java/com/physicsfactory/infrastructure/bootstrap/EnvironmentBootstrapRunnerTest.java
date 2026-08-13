package com.physicsfactory.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.physicsfactory.application.usecase.BootstrapEnvironment;
import com.physicsfactory.application.usecase.PrepareWorkspace;
import com.physicsfactory.application.usecase.ValidateBlenderInstallation;
import com.physicsfactory.domain.exception.BlenderNotFoundException;
import com.physicsfactory.domain.model.WorkspaceLayout;
import com.physicsfactory.support.FixedExecutableProbe;
import com.physicsfactory.support.RecordingDirectoryProvisioner;
import com.physicsfactory.support.RecordingStartupReporter;
import com.physicsfactory.support.WorkspaceLayouts;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

class EnvironmentBootstrapRunnerTest {

    @TempDir
    Path root;

    @Test
    void passesTheConfiguredLayoutAndBlenderLocationToTheUseCase() {
        WorkspaceLayout layout = WorkspaceLayouts.rootedAt(root);
        Path blender = root.resolve("blender").toAbsolutePath();
        RecordingStartupReporter reporter = new RecordingStartupReporter();
        RecordingDirectoryProvisioner provisioner = new RecordingDirectoryProvisioner();
        FixedExecutableProbe probe = FixedExecutableProbe.resolving("blender", blender);

        runner(provisioner, probe, reporter, layout, "blender").run(new DefaultApplicationArguments());

        assertThat(probe.requests()).containsExactly("blender");
        assertThat(provisioner.requests()).contains(layout.root());
        assertThat(reporter.reports()).hasSize(1);
        assertThat(reporter.reports().get(0).blender().executable()).isEqualTo(blender);
    }

    @Test
    void propagatesStartupValidationFailuresSoTheRunIsAborted() {
        WorkspaceLayout layout = WorkspaceLayouts.rootedAt(root);
        EnvironmentBootstrapRunner runner = runner(new RecordingDirectoryProvisioner(),
                FixedExecutableProbe.resolvingNothing(), new RecordingStartupReporter(), layout, "blender");

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(BlenderNotFoundException.class);
    }

    private static EnvironmentBootstrapRunner runner(RecordingDirectoryProvisioner provisioner,
                                                     FixedExecutableProbe probe,
                                                     RecordingStartupReporter reporter,
                                                     WorkspaceLayout layout,
                                                     String blenderLocation) {
        BootstrapEnvironment bootstrapEnvironment = new BootstrapEnvironment(new PrepareWorkspace(provisioner),
                new ValidateBlenderInstallation(probe), reporter);
        return new EnvironmentBootstrapRunner(bootstrapEnvironment, layout, blenderLocation);
    }
}
