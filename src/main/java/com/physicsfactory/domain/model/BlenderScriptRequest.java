package com.physicsfactory.domain.model;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * A request to run one Python script inside Blender.
 *
 * <p>Describes <em>what</em> to run, never <em>how</em> to invoke Blender: the command line flags
 * live in the process adapter, so this record stays free of Blender specifics.
 *
 * @param script    absolute path of the Python script, already resolved inside the render workspace
 * @param arguments arguments passed through to the script
 * @param timeout   how long the invocation may take before it is killed
 */
public record BlenderScriptRequest(Path script, List<String> arguments, Duration timeout) {

    public BlenderScriptRequest {
        Objects.requireNonNull(script, "script must not be null");
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (!script.isAbsolute()) {
            throw new IllegalArgumentException("script must be an absolute path but was: " + script);
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive but was " + timeout);
        }
        arguments = List.copyOf(arguments);
    }

    /** A request without script arguments. */
    public static BlenderScriptRequest of(Path script, Duration timeout) {
        return new BlenderScriptRequest(script, List.of(), timeout);
    }
}
