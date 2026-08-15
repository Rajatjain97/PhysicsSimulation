package com.physicsfactory.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The record of one batch, written beside its videos.
 *
 * <p>{@code createdAt} is an ISO-8601 instant in UTC, the same shape the render manifests use.
 *
 * <p>It answers the two questions worth asking later: what did this batch produce, and how do I make
 * it again. The batch seed, the shared parameters and the quality are all here, so the manifest is a
 * complete recipe even if every video file is deleted - and a folder of preview renders can never be
 * mistaken for publishable ones.
 */
public record BatchManifest(String batchId, String template, int requested, int completed, int failed,
                            long seed, String createdAt, RenderQuality quality,
                            Map<String, Object> parameters, List<BatchEntry> entries) {

    public BatchManifest {
        Objects.requireNonNull(batchId, "batchId must not be null");
        Objects.requireNonNull(template, "template must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(quality, "quality must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        Objects.requireNonNull(entries, "entries must not be null");
        parameters = Map.copyOf(parameters);
        entries = List.copyOf(entries);
    }
}
