package com.physicsfactory.domain.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Everything that was verified while starting up: a provisioned workspace, the Blender scripts that
 * were installed into it, and a usable Blender installation. This is the value future stories will
 * depend on instead of re-checking the environment for themselves.
 *
 * @param workspace         directories that now exist on disk
 * @param installedScripts  Blender scripts present in the render workspace after startup
 * @param blender           the verified Blender executable
 */
public record EnvironmentReport(WorkspacePreparation workspace, List<Path> installedScripts, BlenderInstallation blender) {

    public EnvironmentReport {
        Objects.requireNonNull(workspace, "workspace must not be null");
        Objects.requireNonNull(installedScripts, "installedScripts must not be null");
        Objects.requireNonNull(blender, "blender must not be null");
        installedScripts = List.copyOf(installedScripts);
    }
}
