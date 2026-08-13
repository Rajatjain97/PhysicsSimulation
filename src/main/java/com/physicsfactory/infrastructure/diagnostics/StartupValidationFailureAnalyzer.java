package com.physicsfactory.infrastructure.diagnostics;

import com.physicsfactory.domain.exception.StartupValidationException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns a {@link StartupValidationException} into the framed "APPLICATION FAILED TO START" report
 * instead of a stack trace, giving the operator a description and a concrete next action.
 *
 * <p>Registered through {@code META-INF/spring.factories}: failure analyzers are looked up before the
 * application context exists, so they cannot be beans.
 */
public final class StartupValidationFailureAnalyzer extends AbstractFailureAnalyzer<StartupValidationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, StartupValidationException cause) {
        return new FailureAnalysis(cause.getMessage(), cause.remediation(), cause);
    }
}
