package com.physicsfactory.domain.exception;

import java.util.Objects;

/**
 * Base type for failures in the Java to Blender integration channel.
 *
 * <p>Sibling of {@link StartupValidationException}: that hierarchy describes an environment that is
 * unusable at startup, this one describes a Blender invocation that could not be carried out. Both
 * pair a message with actionable {@link #remediation()} advice so the presentation layer never has to
 * invent one.
 */
public abstract class BlenderIntegrationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String remediation;

    protected BlenderIntegrationException(String message, String remediation) {
        this(message, remediation, null);
    }

    protected BlenderIntegrationException(String message, String remediation, Throwable cause) {
        super(Objects.requireNonNull(message, "message must not be null"), cause);
        this.remediation = Objects.requireNonNull(remediation, "remediation must not be null");
    }

    /** Actionable advice describing how an operator can fix this failure. */
    public String remediation() {
        return remediation;
    }
}
