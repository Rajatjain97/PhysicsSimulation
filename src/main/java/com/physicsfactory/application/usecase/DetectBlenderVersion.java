package com.physicsfactory.application.usecase;

import com.physicsfactory.application.port.BlenderProcessRunner;
import com.physicsfactory.domain.exception.BlenderExecutionException;
import com.physicsfactory.domain.model.BlenderExecution;
import com.physicsfactory.domain.model.BlenderVersion;
import java.time.Duration;
import java.util.Objects;

/**
 * Asks the configured Blender installation which version it is.
 *
 * <p>Story 1.1 proves that the executable exists; this proves that it is actually Blender and can
 * run. Later stories use the version to decide whether a template is supported.
 */
public final class DetectBlenderVersion {

    private final BlenderProcessRunner processRunner;
    private final Duration timeout;

    public DetectBlenderVersion(BlenderProcessRunner processRunner, Duration timeout) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    }

    /**
     * @throws BlenderExecutionException if the probe failed or its output contained no version
     */
    public BlenderVersion execute() {
        BlenderExecution execution = processRunner.runVersionProbe(timeout);
        if (!execution.isSuccessful()) {
            throw new BlenderExecutionException(
                    "Blender version probe exited with code " + execution.exitCode() + ".", execution);
        }
        return BlenderVersion.parse(execution.standardOutput())
                .orElseThrow(() -> new BlenderExecutionException(
                        "Could not read a Blender version from the probe output.", execution));
    }
}
