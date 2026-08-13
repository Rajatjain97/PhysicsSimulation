package com.physicsfactory.domain.model;

import java.util.Objects;

/**
 * Everything that was verified while starting up: a provisioned workspace and a usable Blender
 * installation. This is the value future stories will depend on instead of re-checking the
 * environment for themselves.
 */
public record EnvironmentReport(WorkspacePreparation workspace, BlenderInstallation blender) {

    public EnvironmentReport {
        Objects.requireNonNull(workspace, "workspace must not be null");
        Objects.requireNonNull(blender, "blender must not be null");
    }
}
