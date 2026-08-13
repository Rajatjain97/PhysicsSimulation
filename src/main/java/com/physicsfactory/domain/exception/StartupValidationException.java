package com.physicsfactory.domain.exception;

import java.util.Objects;

/**
 * Base type for problems that make it impossible to start Physics Factory in a usable state.
 *
 * <p>Every instance carries a human readable {@link #remediation()} hint. The presentation layer
 * (see {@code StartupValidationFailureAnalyzer}) turns that pair into the message the operator
 * sees, which is why the domain never needs to know how failures are rendered or logged.
 */
public abstract class StartupValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String remediation;

    protected StartupValidationException(String message, String remediation) {
        this(message, remediation, null);
    }

    protected StartupValidationException(String message, String remediation, Throwable cause) {
        super(Objects.requireNonNull(message, "message must not be null"), cause);
        this.remediation = Objects.requireNonNull(remediation, "remediation must not be null");
    }

    /** Actionable advice describing how an operator can fix this failure. */
    public String remediation() {
        return remediation;
    }
}
