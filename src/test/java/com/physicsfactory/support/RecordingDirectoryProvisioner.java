package com.physicsfactory.support;

import com.physicsfactory.application.port.DirectoryProvisioner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * In-memory {@link DirectoryProvisioner} that records what it was asked to create. Preferred over a
 * mocking framework here: the behaviour under test is "which directories, in which order".
 */
public final class RecordingDirectoryProvisioner implements DirectoryProvisioner {

    private final Set<Path> existing = new LinkedHashSet<>();
    private final List<Path> requests = new ArrayList<>();

    public RecordingDirectoryProvisioner(Path... alreadyExisting) {
        existing.addAll(List.of(alreadyExisting));
    }

    @Override
    public boolean ensureDirectoryExists(Path directory) {
        requests.add(directory);
        return existing.add(directory);
    }

    public List<Path> requests() {
        return List.copyOf(requests);
    }
}
