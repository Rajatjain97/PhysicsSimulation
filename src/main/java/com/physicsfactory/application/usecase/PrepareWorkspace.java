package com.physicsfactory.application.usecase;

import com.physicsfactory.application.port.DirectoryProvisioner;
import com.physicsfactory.domain.model.WorkspaceLayout;
import com.physicsfactory.domain.model.WorkspacePreparation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates every directory described by a {@link WorkspaceLayout}, skipping the ones that already
 * exist. The operation is idempotent, so it is safe to run on every start.
 */
public final class PrepareWorkspace {

    private final DirectoryProvisioner directoryProvisioner;

    public PrepareWorkspace(DirectoryProvisioner directoryProvisioner) {
        this.directoryProvisioner = Objects.requireNonNull(directoryProvisioner, "directoryProvisioner must not be null");
    }

    public WorkspacePreparation execute(WorkspaceLayout layout) {
        Objects.requireNonNull(layout, "layout must not be null");

        List<Path> created = new ArrayList<>();
        if (directoryProvisioner.ensureDirectoryExists(layout.root())) {
            created.add(layout.root());
        }
        for (Path directory : layout.allDirectories()) {
            if (directoryProvisioner.ensureDirectoryExists(directory)) {
                created.add(directory);
            }
        }
        return new WorkspacePreparation(layout, created);
    }
}
