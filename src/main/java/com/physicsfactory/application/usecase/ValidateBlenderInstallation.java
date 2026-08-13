package com.physicsfactory.application.usecase;

import com.physicsfactory.application.port.ExecutableProbe;
import com.physicsfactory.domain.exception.BlenderNotFoundException;
import com.physicsfactory.domain.model.BlenderInstallation;
import java.util.Objects;

/**
 * Turns a configured Blender location into a verified {@link BlenderInstallation}, or fails.
 */
public final class ValidateBlenderInstallation {

    private final ExecutableProbe executableProbe;

    public ValidateBlenderInstallation(ExecutableProbe executableProbe) {
        this.executableProbe = Objects.requireNonNull(executableProbe, "executableProbe must not be null");
    }

    /**
     * @throws BlenderNotFoundException if the configured location is blank, missing, or not executable
     */
    public BlenderInstallation execute(String configuredLocation) {
        if (configuredLocation == null || configuredLocation.isBlank()) {
            throw new BlenderNotFoundException("<not configured>");
        }
        return executableProbe.resolve(configuredLocation)
                .map(BlenderInstallation::new)
                .orElseThrow(() -> new BlenderNotFoundException(configuredLocation));
    }
}
