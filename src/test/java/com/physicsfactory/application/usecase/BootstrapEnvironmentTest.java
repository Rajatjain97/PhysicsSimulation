package com.physicsfactory.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.physicsfactory.domain.exception.BlenderNotFoundException;
import com.physicsfactory.domain.model.EnvironmentReport;
import com.physicsfactory.domain.model.WorkspaceLayout;
import com.physicsfactory.support.FixedExecutableProbe;
import com.physicsfactory.support.RecordingDirectoryProvisioner;
import com.physicsfactory.support.RecordingStartupReporter;
import com.physicsfactory.support.WorkspaceLayouts;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BootstrapEnvironmentTest {

    private static final String CONFIGURED_BLENDER = "blender";

    @TempDir
    Path root;

    @Test
    void provisionsTheWorkspaceValidatesBlenderAndReportsTheResult() {
        WorkspaceLayout layout = WorkspaceLayouts.rootedAt(root);
        Path blender = root.resolve("blender").toAbsolutePath();
        RecordingDirectoryProvisioner provisioner = new RecordingDirectoryProvisioner();
        RecordingStartupReporter reporter = new RecordingStartupReporter();

        EnvironmentReport report = bootstrap(provisioner, FixedExecutableProbe.resolving(CONFIGURED_BLENDER, blender), reporter)
                .execute(new BootstrapRequest(layout, CONFIGURED_BLENDER));

        assertThat(report.blender().executable()).isEqualTo(blender);
        assertThat(report.workspace().layout()).isEqualTo(layout);
        assertThat(provisioner.requests()).contains(layout.allDirectories().toArray(Path[]::new));
        assertThat(reporter.reports()).containsExactly(report);
    }

    @Test
    void provisionsTheWorkspaceBeforeValidatingBlenderSoTheLogDirectoryExists() {
        WorkspaceLayout layout = WorkspaceLayouts.rootedAt(root);
        RecordingDirectoryProvisioner provisioner = new RecordingDirectoryProvisioner();
        RecordingStartupReporter reporter = new RecordingStartupReporter();
        BootstrapEnvironment bootstrap = bootstrap(provisioner, FixedExecutableProbe.resolvingNothing(), reporter);

        assertThatThrownBy(() -> bootstrap.execute(new BootstrapRequest(layout, CONFIGURED_BLENDER)))
                .isInstanceOf(BlenderNotFoundException.class);

        assertThat(provisioner.requests()).containsAll(layout.allDirectories());
        assertThat(reporter.reports()).isEmpty();
    }

    private static BootstrapEnvironment bootstrap(RecordingDirectoryProvisioner provisioner,
                                                 FixedExecutableProbe probe,
                                                 RecordingStartupReporter reporter) {
        return new BootstrapEnvironment(new PrepareWorkspace(provisioner),
                new ValidateBlenderInstallation(probe),
                reporter);
    }
}
