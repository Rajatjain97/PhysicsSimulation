package com.physicsfactory.domain.model;

import com.physicsfactory.domain.exception.InvalidSceneContractException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * A request to render the same template many times, each with its own seed.
 *
 * <p>The batch seed is the whole reproducibility story: every video's seed is derived from it and its
 * index, so re-running a batch with the same seed rebuilds the same set of scenes. It is resolved
 * before the batch starts - generated if the operator did not choose one - and recorded in the batch
 * manifest, never left implicit.
 *
 * @param template         template every video in the batch uses
 * @param count            how many videos to render
 * @param outputDirectory  where the batch writes, relative to the workspace root
 * @param seed             the batch seed
 * @param parameters       parameters shared by every video; each video adds only its own seed
 * @param timeout          render budget for a single video
 * @param quality          how much rendering cost each video in the batch may pay
 */
public record BatchRequest(String template, int count, Path outputDirectory, long seed,
                           Map<String, Object> parameters, Duration timeout, RenderQuality quality) {

    public BatchRequest {
        Objects.requireNonNull(template, "template must not be null");
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        Objects.requireNonNull(quality, "quality must not be null");
        if (template.isBlank()) {
            throw new InvalidSceneContractException("A batch needs a template.");
        }
        if (count <= 0) {
            throw new InvalidSceneContractException("A batch needs at least one video but asked for " + count + ".");
        }
        if (outputDirectory.isAbsolute()) {
            throw new InvalidSceneContractException(
                    "Batch output '" + outputDirectory + "' must be relative to the workspace root.");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new InvalidSceneContractException("Batch timeout must be positive but was " + timeout + ".");
        }
        parameters = Map.copyOf(parameters);
    }
}
