package com.physicsfactory.domain.exception;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Raised when a required workspace directory exists as something else, or cannot be created because
 * of an I/O or permission problem.
 */
public final class WorkspaceProvisioningException extends StartupValidationException {

    private static final long serialVersionUID = 1L;

    private final transient Path directory;

    public WorkspaceProvisioningException(Path directory, String reason, Throwable cause) {
        super("Cannot provision workspace directory '" + directory + "': " + reason,
                "Check that the path is writable and that no file with the same name exists, "
                        + "then restart Physics Factory. The workspace root is configured by "
                        + "'physics-factory.workspace.root'.",
                cause);
        this.directory = Objects.requireNonNull(directory, "directory must not be null");
    }

    /** The directory that could not be provisioned. */
    public Path directory() {
        return directory;
    }
}
