package com.physicsfactory.domain.exception;

import java.util.Objects;

/**
 * Raised when the configured Blender executable cannot be found or is not executable.
 *
 * <p>Blender is a hard requirement for every future rendering story, so the application refuses to
 * continue running without it rather than failing later, halfway through a render.
 */
public final class BlenderNotFoundException extends StartupValidationException {

    private static final long serialVersionUID = 1L;

    private final String configuredLocation;

    public BlenderNotFoundException(String configuredLocation) {
        super("Blender executable '" + configuredLocation + "' was not found or is not executable.",
                "Install Blender and set 'physics-factory.blender.executable-path' in application.yaml "
                        + "(or the BLENDER_EXECUTABLE environment variable) to the full path of the "
                        + "Blender binary, for example /Applications/Blender.app/Contents/MacOS/Blender "
                        + "or C:\\Program Files\\Blender Foundation\\Blender 4.2\\blender.exe.");
        this.configuredLocation = Objects.requireNonNull(configuredLocation, "configuredLocation must not be null");
    }

    /** The raw value that was configured, kept verbatim so operators recognise their own input. */
    public String configuredLocation() {
        return configuredLocation;
    }
}
