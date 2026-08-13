package com.physicsfactory.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a single render job.
 *
 * <p>A wrapper rather than a bare {@link UUID} so that batch generation cannot accidentally pass a
 * scene id where a job id belongs, and so the id can gain fields later without touching call sites.
 */
public record RenderJobId(UUID value) {

    public RenderJobId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static RenderJobId newId() {
        return new RenderJobId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
