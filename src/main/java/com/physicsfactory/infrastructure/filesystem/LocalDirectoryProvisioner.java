package com.physicsfactory.infrastructure.filesystem;

import com.physicsfactory.application.port.DirectoryProvisioner;
import com.physicsfactory.domain.exception.WorkspaceProvisioningException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates directories on the local filesystem.
 *
 * <p>The only adapter that is allowed to touch {@link Files} for directory creation, which keeps the
 * "where do folders come from" question answerable in one place.
 */
public final class LocalDirectoryProvisioner implements DirectoryProvisioner {

    private static final Logger log = LoggerFactory.getLogger(LocalDirectoryProvisioner.class);

    @Override
    public boolean ensureDirectoryExists(Path directory) {
        Objects.requireNonNull(directory, "directory must not be null");

        if (Files.isDirectory(directory)) {
            log.debug("Directory already present: {}", directory);
            return false;
        }
        if (Files.exists(directory)) {
            throw new WorkspaceProvisioningException(directory, "a non-directory file already exists there", null);
        }
        try {
            Files.createDirectories(directory);
            log.debug("Created directory: {}", directory);
            return true;
        } catch (FileAlreadyExistsException e) {
            // Lost a race with another process, or a path segment is a regular file.
            if (Files.isDirectory(directory)) {
                return false;
            }
            throw new WorkspaceProvisioningException(directory, "a non-directory file already exists there", e);
        } catch (IOException | SecurityException e) {
            throw new WorkspaceProvisioningException(directory, e.getMessage(), e);
        }
    }
}
