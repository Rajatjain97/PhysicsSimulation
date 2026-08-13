package com.physicsfactory.domain.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of making a {@link WorkspaceLayout} real on disk.
 *
 * @param layout              the layout that is now guaranteed to exist
 * @param createdDirectories  directories that did not exist before this run, in creation order
 */
public record WorkspacePreparation(WorkspaceLayout layout, List<Path> createdDirectories) {

    public WorkspacePreparation {
        Objects.requireNonNull(layout, "layout must not be null");
        Objects.requireNonNull(createdDirectories, "createdDirectories must not be null");
        createdDirectories = List.copyOf(createdDirectories);
    }

    /** {@code true} when the workspace was already fully provisioned before this run. */
    public boolean wasAlreadyProvisioned() {
        return createdDirectories.isEmpty();
    }
}
