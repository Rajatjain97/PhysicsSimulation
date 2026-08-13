package com.physicsfactory.application.usecase;

import com.physicsfactory.application.port.BlenderScriptLibrary;
import com.physicsfactory.application.port.StartupReporter;
import com.physicsfactory.domain.exception.StartupValidationException;
import com.physicsfactory.domain.model.BlenderInstallation;
import com.physicsfactory.domain.model.EnvironmentReport;
import com.physicsfactory.domain.model.WorkspacePreparation;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The startup sequence: provision the workspace, install the Blender scripts into it, verify Blender,
 * publish what was found.
 *
 * <p>The order is deliberate. Directories come first so that the log directory exists and a later
 * failure is captured on disk. Scripts are installed next because that completes provisioning and
 * needs no Blender. Blender itself is verified last, so a machine without Blender still ends up with
 * a fully prepared workspace.
 */
public final class BootstrapEnvironment {

    private final PrepareWorkspace prepareWorkspace;
    private final BlenderScriptLibrary blenderScriptLibrary;
    private final ValidateBlenderInstallation validateBlenderInstallation;
    private final StartupReporter startupReporter;

    public BootstrapEnvironment(PrepareWorkspace prepareWorkspace,
                                BlenderScriptLibrary blenderScriptLibrary,
                                ValidateBlenderInstallation validateBlenderInstallation,
                                StartupReporter startupReporter) {
        this.prepareWorkspace = Objects.requireNonNull(prepareWorkspace, "prepareWorkspace must not be null");
        this.blenderScriptLibrary = Objects.requireNonNull(blenderScriptLibrary, "blenderScriptLibrary must not be null");
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
        List<Path> installedScripts = blenderScriptLibrary.installBundledScripts();
        BlenderInstallation blender = validateBlenderInstallation.execute(request.blenderExecutableLocation());

        EnvironmentReport report = new EnvironmentReport(workspace, installedScripts, blender);
        startupReporter.report(report);
        return report;
    }
}
