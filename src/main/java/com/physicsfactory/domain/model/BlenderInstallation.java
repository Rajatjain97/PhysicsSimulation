package com.physicsfactory.domain.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A verified Blender installation.
 *
 * <p>The type exists to make "this path has been checked" provable at compile time: rendering code
 * added by later stories accepts a {@code BlenderInstallation} rather than a raw {@link Path}, so an
 * unverified path cannot reach it.
 *
 * @param executable absolute path to the Blender binary
 */
public record BlenderInstallation(Path executable) {

    public BlenderInstallation {
        Objects.requireNonNull(executable, "executable must not be null");
        if (!executable.isAbsolute()) {
            throw new IllegalArgumentException("Blender executable path must be absolute but was: " + executable);
        }
    }
}
