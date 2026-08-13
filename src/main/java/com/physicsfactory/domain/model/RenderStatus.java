package com.physicsfactory.domain.model;

/**
 * How a render job ended.
 *
 * <p>Only terminal outcomes are modelled. Queued and in-flight states belong to the batch scheduler a
 * later story will add; inventing them now would mean shipping constants nothing can produce.
 */
public enum RenderStatus {

    /** Blender finished and reported success. */
    SUCCEEDED,

    /** Blender ran but reported a non-zero exit code. */
    FAILED;

    public boolean isSuccessful() {
        return this == SUCCEEDED;
    }
}
