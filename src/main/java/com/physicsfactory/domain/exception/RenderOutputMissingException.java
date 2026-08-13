package com.physicsfactory.domain.exception;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Raised when Blender reported success but the file it was asked to produce is not on disk.
 *
 * <p>Separate from {@link BlenderExecutionException} because the process itself was fine: the
 * contract between Java and the render script was not honoured, which is a different problem to
 * diagnose.
 */
public final class RenderOutputMissingException extends BlenderIntegrationException {

    private static final long serialVersionUID = 1L;

    private final transient Path expectedOutput;

    public RenderOutputMissingException(Path expectedOutput, String commandLine) {
        super("Blender exited successfully but produced no file at " + expectedOutput + ".",
                "Run the render manually to see what Blender wrote instead: " + commandLine);
        this.expectedOutput = Objects.requireNonNull(expectedOutput, "expectedOutput must not be null");
    }

    /** Where the file was expected. */
    public Path expectedOutput() {
        return expectedOutput;
    }
}
