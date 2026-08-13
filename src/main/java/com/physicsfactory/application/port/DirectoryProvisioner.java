package com.physicsfactory.application.port;

import com.physicsfactory.domain.exception.WorkspaceProvisioningException;
import java.nio.file.Path;

/**
 * Outbound port for creating directories. Implemented by the filesystem adapter; a later story that
 * writes to object storage would add a second implementation rather than change the use cases.
 */
public interface DirectoryProvisioner {

    /**
     * Ensures {@code directory} exists, creating it and any missing parents.
     *
     * @return {@code true} if the directory had to be created, {@code false} if it already existed
     * @throws WorkspaceProvisioningException if the directory cannot be created
     */
    boolean ensureDirectoryExists(Path directory);
}
