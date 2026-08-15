package com.physicsfactory.domain.model;

import java.util.Objects;

/**
 * What happened to one video in a batch.
 *
 * <p>Enough to find the file, reproduce it, or understand why it is missing. A failed entry keeps its
 * seed and its reason, which is what makes a failure worth reading rather than just a smaller count.
 * A batch that was interrupted simply has fewer entries than it requested, which the manifest's counts
 * make obvious.
 *
 * @param index      one-based position in the batch
 * @param seed       the seed this video was rendered with
 * @param status     how the render ended
 * @param video      workspace-relative video path, empty when nothing was produced
 * @param manifest   workspace-relative render manifest path, empty when nothing was produced
 * @param durationMs how long the render took, or -1 when it never ran
 * @param error      why it failed, empty when it did not
 */
public record BatchEntry(int index, long seed, RenderStatus status, String video, String manifest,
                         long durationMs, String error) {

    public BatchEntry {
        Objects.requireNonNull(video, "video must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");
        Objects.requireNonNull(error, "error must not be null");
    }

    public static BatchEntry succeeded(int index, long seed, String video, String manifest, long durationMs) {
        return new BatchEntry(index, seed, RenderStatus.SUCCEEDED, video, manifest, durationMs, "");
    }

    public static BatchEntry failed(int index, long seed, String error, long durationMs) {
        return new BatchEntry(index, seed, RenderStatus.FAILED, "", "", durationMs, error);
    }

    public boolean isSuccessful() {
        return status == RenderStatus.SUCCEEDED;
    }
}
