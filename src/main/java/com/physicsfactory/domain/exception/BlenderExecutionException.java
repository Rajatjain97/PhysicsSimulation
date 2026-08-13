package com.physicsfactory.domain.exception;

import com.physicsfactory.domain.model.BlenderExecution;
import java.util.Optional;

/**
 * Raised when Blender could not be started, or finished in a way the caller cannot recover from.
 *
 * <p>A non-zero exit code on its own is <em>not</em> an exception: the process runner returns it as a
 * {@link BlenderExecution} so the caller can inspect stderr and decide. This exception is for the
 * cases where there is nothing sensible to hand back - the process would not start, or a use case
 * required success and did not get it.
 */
public final class BlenderExecutionException extends BlenderIntegrationException {

    private static final long serialVersionUID = 1L;

    private final transient BlenderExecution execution;

    /** The process ran but produced an unusable result. */
    public BlenderExecutionException(String message, BlenderExecution execution) {
        super(message, "Reproduce it by running the command manually: " + execution.commandLine());
        this.execution = execution;
    }

    /** The process could not be started at all. */
    public BlenderExecutionException(String message, Throwable cause) {
        super(message,
                "Check that the configured Blender executable is a real Blender binary and that the "
                        + "workspace root is readable, then try again.",
                cause);
        this.execution = null;
    }

    /** The captured execution, absent when Blender never started. */
    public Optional<BlenderExecution> execution() {
        return Optional.ofNullable(execution);
    }
}
