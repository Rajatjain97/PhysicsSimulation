package com.physicsfactory.domain.model;

import com.physicsfactory.domain.exception.WorkspaceConfigurationException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, fully resolved description of where Physics Factory keeps its files on disk.
 *
 * <p>Instances are created through {@link #of(Path, Map)}, which validates the configured layout and
 * turns relative paths into absolute ones. Once constructed, a layout is guaranteed to contain an
 * absolute path for every {@link WorkspaceDirectory}, so downstream code never has to null check or
 * re-resolve anything.
 */
public record WorkspaceLayout(Path root, Map<WorkspaceDirectory, Path> directories) {

    public WorkspaceLayout(Path root, Map<WorkspaceDirectory, Path> directories) {
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(directories, "directories must not be null");
        requireComplete(directories.keySet());
        this.root = root;
        // EnumMap keeps iteration in declaration order, which makes logging and tests deterministic.
        this.directories = Collections.unmodifiableMap(new EnumMap<>(directories));
    }

    /**
     * Builds a layout from a workspace root and the configured relative paths.
     *
     * @param root          workspace root; resolved against the working directory when relative
     * @param relativePaths one relative path per {@link WorkspaceDirectory}
     * @throws WorkspaceConfigurationException if a mapping is missing, blank, absolute, or points
     *                                         outside the workspace root
     */
    public static WorkspaceLayout of(Path root, Map<WorkspaceDirectory, String> relativePaths) {
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(relativePaths, "relativePaths must not be null");
        requireComplete(relativePaths.keySet());

        Path normalisedRoot = root.toAbsolutePath().normalize();
        Map<WorkspaceDirectory, Path> resolved = new EnumMap<>(WorkspaceDirectory.class);
        relativePaths.forEach((directory, relativePath) ->
                resolved.put(directory, resolveWithinRoot(normalisedRoot, directory, relativePath)));
        return new WorkspaceLayout(normalisedRoot, resolved);
    }

    /** The absolute path of the given directory. */
    public Path pathOf(WorkspaceDirectory directory) {
        Objects.requireNonNull(directory, "directory must not be null");
        return directories.get(directory);
    }

    /** All managed directories, in {@link WorkspaceDirectory} declaration order. */
    public List<Path> allDirectories() {
        return List.copyOf(directories.values());
    }

    private static void requireComplete(Set<WorkspaceDirectory> configured) {
        Set<WorkspaceDirectory> missing = EnumSet.allOf(WorkspaceDirectory.class);
        missing.removeAll(configured);
        if (!missing.isEmpty()) {
            String missingKeys = missing.stream().map(WorkspaceDirectory::configKey).toList().toString();
            throw new WorkspaceConfigurationException(
                    "Workspace layout is missing a path for " + missingKeys + ".",
                    "Add the missing entries under 'physics-factory.workspace.directories' in application.yaml.");
        }
    }

    private static Path resolveWithinRoot(Path root, WorkspaceDirectory directory, String relativePath) {
        String configKey = "physics-factory.workspace.directories." + directory.configKey();
        if (relativePath == null || relativePath.isBlank()) {
            throw new WorkspaceConfigurationException(
                    "No path configured for workspace directory '" + directory.configKey() + "'.",
                    "Set '" + configKey + "' to a path relative to the workspace root.");
        }
        Path candidate;
        try {
            candidate = Path.of(relativePath.trim());
        } catch (InvalidPathException e) {
            throw new WorkspaceConfigurationException(
                    "Path '" + relativePath + "' configured for '" + directory.configKey() + "' is not a valid path.",
                    "Set '" + configKey + "' to a valid path relative to the workspace root.");
        }
        if (candidate.isAbsolute()) {
            throw new WorkspaceConfigurationException(
                    "Path '" + relativePath + "' configured for '" + directory.configKey() + "' must be relative "
                            + "to the workspace root.",
                    "Set '" + configKey + "' to a relative path and move the absolute part into "
                            + "'physics-factory.workspace.root'.");
        }
        Path resolved = root.resolve(candidate).normalize();
        if (!resolved.startsWith(root)) {
            throw new WorkspaceConfigurationException(
                    "Path '" + relativePath + "' configured for '" + directory.configKey() + "' escapes the "
                            + "workspace root '" + root + "'.",
                    "Remove the '..' segments from '" + configKey + "' so the directory stays inside the workspace.");
        }
        return resolved;
    }
}
