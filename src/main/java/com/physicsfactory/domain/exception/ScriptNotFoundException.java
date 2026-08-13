package com.physicsfactory.domain.exception;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Raised when a Blender Python script cannot be resolved inside the render workspace.
 */
public final class ScriptNotFoundException extends BlenderIntegrationException {

    private static final long serialVersionUID = 1L;

    private final String scriptName;

    public ScriptNotFoundException(String scriptName, Path scriptsDirectory) {
        super("Blender script '" + scriptName + "' was not found in " + scriptsDirectory + ".",
                "Bundled scripts live in src/main/resources/blender/scripts and are installed into the "
                        + "render workspace on startup. Add the script there and restart, or copy it into "
                        + scriptsDirectory + " manually.");
        this.scriptName = Objects.requireNonNull(scriptName, "scriptName must not be null");
    }

    /** The script that was requested, as the caller named it. */
    public String scriptName() {
        return scriptName;
    }
}
