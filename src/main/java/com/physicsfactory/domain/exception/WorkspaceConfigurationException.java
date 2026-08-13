package com.physicsfactory.domain.exception;

/**
 * Raised when the configured workspace layout is incomplete or unsafe, for example a missing
 * directory mapping or a relative path that escapes the workspace root.
 */
public final class WorkspaceConfigurationException extends StartupValidationException {

    private static final long serialVersionUID = 1L;

    public WorkspaceConfigurationException(String message, String remediation) {
        super(message, remediation);
    }
}
