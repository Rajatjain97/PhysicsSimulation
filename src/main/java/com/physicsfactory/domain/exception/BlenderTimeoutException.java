package com.physicsfactory.domain.exception;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Raised when a Blender invocation exceeded its configured timeout and was killed.
 *
 * <p>Kept separate from {@link BlenderExecutionException} because the remedy is different: a render
 * that is simply slow needs a larger budget, not a fix.
 */
public final class BlenderTimeoutException extends BlenderIntegrationException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> command;
    private final Duration timeout;

    public BlenderTimeoutException(List<String> command, Duration timeout) {
        super("Blender did not finish within " + Objects.requireNonNull(timeout, "timeout must not be null")
                        + " and was terminated: " + String.join(" ", command),
                "Increase the matching timeout under 'physics-factory.blender' in application.yaml, or "
                        + "simplify the scene so it renders inside the current budget.");
        this.command = List.copyOf(command);
        this.timeout = timeout;
    }

    /** The full command line that was killed. */
    public List<String> command() {
        return command;
    }

    /** The budget that was exceeded. */
    public Duration timeout() {
        return timeout;
    }
}
