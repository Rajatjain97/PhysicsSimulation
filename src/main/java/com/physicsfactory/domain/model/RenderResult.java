package com.physicsfactory.domain.model;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of a render job, with the raw Blender execution attached so a caller can inspect
 * stderr without re-running anything.
 *
 * <p>{@code outputFile} is empty for jobs that legitimately produce no video, such as the
 * healthcheck. Timeouts are not represented here: they surface as
 * {@link com.physicsfactory.domain.exception.BlenderTimeoutException} because there is no execution
 * to report.
 *
 * @param jobId      the job this result belongs to
 * @param status     how the job ended
 * @param execution  what Blender did
 * @param outputFile the produced file, when there is one
 */
public record RenderResult(RenderJobId jobId, RenderStatus status, BlenderExecution execution, Optional<Path> outputFile) {

    public RenderResult {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(execution, "execution must not be null");
        Objects.requireNonNull(outputFile, "outputFile must not be null");
    }

    /** A job that finished without producing a file, such as the healthcheck. */
    public static RenderResult succeeded(RenderJobId jobId, BlenderExecution execution) {
        return new RenderResult(jobId, RenderStatus.SUCCEEDED, execution, Optional.empty());
    }

    /** A job that produced a file. */
    public static RenderResult succeeded(RenderJobId jobId, BlenderExecution execution, Path outputFile) {
        return new RenderResult(jobId, RenderStatus.SUCCEEDED, execution, Optional.of(outputFile));
    }

    public static RenderResult failed(RenderJobId jobId, BlenderExecution execution) {
        return new RenderResult(jobId, RenderStatus.FAILED, execution, Optional.empty());
    }

    public boolean isSuccessful() {
        return status.isSuccessful();
    }
}
