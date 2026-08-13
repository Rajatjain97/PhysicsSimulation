package com.physicsfactory.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A render request that has been accepted: it has an identity, a scene contract, and a submission
 * time.
 *
 * <p>The job owns the names of the files it needs, so nothing downstream has to invent naming
 * conventions.
 *
 * @param id          identity used for file names and, later, batch tracking
 * @param request     what was asked for
 * @param scene       the contract that will be handed to Blender
 * @param submittedAt when the job was created
 */
public record RenderJob(RenderJobId id, RenderRequest request, SceneContract scene, Instant submittedAt) {

    public RenderJob {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(scene, "scene must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
    }

    /** Accepts a request, deriving the scene contract from it. */
    public static RenderJob create(RenderRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return new RenderJob(RenderJobId.newId(), request, SceneContract.forRequest(request), Instant.now());
    }

    /** File name of this job's scene contract inside the render cache. */
    public String sceneFileName() {
        return id + ".scene.json";
    }
}
