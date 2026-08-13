package com.physicsfactory.application.usecase;

import com.physicsfactory.application.port.StartupReporter;
import com.physicsfactory.domain.exception.StartupValidationException;
import com.physicsfactory.domain.model.BlenderInstallation;
import com.physicsfactory.domain.model.EnvironmentReport;
import com.physicsfactory.domain.model.WorkspacePreparation;
import java.util.Objects;

/**
 * The Story 1.1 entry point: provision the workspace, verify Blender, publish what was found.
 *
 * <p>The workspace is prepared <em>before</em> Blender is validated so that the log directory exists
 * and the failure is captured on disk when Blender is missing.
 */
public final class BootstrapEnvironment {

    private final PrepareWorkspace prepareWorkspace;
    private final ValidateBlenderInstallation validateBlenderInstallation;
    private final StartupReporter startupReporter;

    public BootstrapEnvironment(PrepareWorkspace prepareWorkspace,
                                ValidateBlenderInstallation validateBlenderInstallation,
                                StartupReporter startupReporter) {
        this.prepareWorkspace = Objects.requireNonNull(prepareWorkspace, "prepareWorkspace must not be null");
        this.validateBlenderInstallation =
                Objects.requireNonNull(validateBlenderInstallation, "validateBlenderInstallation must not be null");
        this.startupReporter = Objects.requireNonNull(startupReporter, "startupReporter must not be null");
    }

    /**
     * @throws StartupValidationException if the workspace cannot be provisioned or Blender is unusable
     */
    public EnvironmentReport execute(BootstrapRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        WorkspacePreparation workspace = prepareWorkspace.execute(request.workspace());
        BlenderInstallation blender = validateBlenderInstallation.execute(request.blenderExecutableLocation());

        EnvironmentReport report = new EnvironmentReport(workspace, blender);
        startupReporter.report(report);
        return report;
    }
}
