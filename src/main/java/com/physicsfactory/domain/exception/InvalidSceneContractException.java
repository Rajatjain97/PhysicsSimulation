package com.physicsfactory.domain.exception;

/**
 * Raised when a scene contract - the JSON document Java hands to Blender - is malformed, or when the
 * render request it would be built from violates the contract's rules.
 */
public final class InvalidSceneContractException extends BlenderIntegrationException {

    private static final long serialVersionUID = 1L;

    public InvalidSceneContractException(String message) {
        super(message,
                "The scene contract is the agreement between Java and Blender: 'sceneVersion' must be the "
                        + "current version, 'template' must name an installed Blender script, and 'output' must "
                        + "be a path relative to the workspace root.");
    }

    public InvalidSceneContractException(String message, Throwable cause) {
        super(message,
                "The scene contract could not be serialised. This is a programming error in the render "
                        + "pipeline rather than a configuration problem.",
                cause);
    }
}
