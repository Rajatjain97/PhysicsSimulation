package com.physicsfactory.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.physicsfactory.domain.exception.BlenderNotFoundException;
import com.physicsfactory.domain.model.EnvironmentReport;
import com.physicsfactory.domain.model.WorkspaceDirectory;
import com.physicsfactory.domain.model.WorkspaceLayout;
import com.physicsfactory.support.FixedExecutableProbe;
import com.physicsfactory.support.InMemoryBlenderScriptLibrary;
import com.physicsfactory.support.RecordingDirectoryProvisioner;
import com.physicsfactory.support.RecordingStartupReporter;
import com.physicsfactory.support.WorkspaceLayouts;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BootstrapEnvironmentTest {

    private static final String CONFIGURED_BLENDER = "blender";
    private static final String HEALTHCHECK_SCRIPT = "healthcheck.py";

    @TempDir
    Path root;

    @Test
    void provisionsTheWorkspaceInstallsScriptsValidatesBlenderAndReportsTheResult() {
        WorkspaceLayout layout = WorkspaceLayouts.rootedAt(root);
        Path blender = root.resolve("blender").toAbsolutePath();
        Path script = layout.pathOf(WorkspaceDirectory.BLENDER_SCRIPTS).resolve(HEALTHCHECK_SCRIPT);
        RecordingDirectoryProvisioner provisioner = new RecordingDirectoryProvisioner();
        RecordingStartupReporter reporter = new RecordingStartupReporter();
        InMemoryBlenderScriptLibrary scriptLibrary =
                InMemoryBlenderScriptLibrary.containing(HEALTHCHECK_SCRIPT, script);

        EnvironmentReport report = bootstrap(provisioner, scriptLibrary,
                FixedExecutableProbe.resolving(CONFIGURED_BLENDER, blender), reporter)
                .execute(new BootstrapRequest(layout, CONFIGURED_BLENDER));

        assertThat(report.blender().executable()).isEqualTo(blender);
        assertThat(report.workspace().layout()).isEqualTo(layout);
        assertThat(report.installedScripts()).containsExactly(script);
        assertThat(scriptLibrary.installations()).isEqualTo(1);
        assertThat(provisioner.requests()).contains(layout.allDirectories().toArray(Path[]::new));
        assertThat(reporter.reports()).containsExactly(report);
    }

    @Test
    void provisionsTheWorkspaceAndScriptsBeforeValidatingBlender() {
        WorkspaceLayout layout = WorkspaceLayouts.rootedAt(root);
        RecordingDirectoryProvisioner provisioner = new RecordingDirectoryProvisioner();
        RecordingStartupReporter reporter = new RecordingStartupReporter();
        InMemoryBlenderScriptLibrary scriptLibrary = InMemoryBlenderScriptLibrary.empty();
        BootstrapEnvironment bootstrap =
                bootstrap(provisioner, scriptLibrary, FixedExecutableProbe.resolvingNothing(), reporter);

        assertThatThrownBy(() -> bootstrap.execute(new BootstrapRequest(layout, CONFIGURED_BLENDER)))
                .isInstanceOf(BlenderNotFoundException.class);

        assertThat(provisioner.requests()).containsAll(layout.allDirectories());
        assertThat(scriptLibrary.installations()).isEqualTo(1);
        assertThat(reporter.reports()).isEmpty();
    }

    private static BootstrapEnvironment bootstrap(RecordingDirectoryProvisioner provisioner,
                                                  InMemoryBlenderScriptLibrary scriptLibrary,
                                                  FixedExecutableProbe probe,
                                                  RecordingStartupReporter reporter) {
        return new BootstrapEnvironment(new PrepareWorkspace(provisioner),
                scriptLibrary,
                new ValidateBlenderInstallation(probe),
                reporter);
    }
}
