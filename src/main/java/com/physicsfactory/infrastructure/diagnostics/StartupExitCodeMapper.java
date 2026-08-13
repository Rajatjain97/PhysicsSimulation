package com.physicsfactory.infrastructure.diagnostics;

import com.physicsfactory.domain.exception.StartupValidationException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.boot.ExitCodeExceptionMapper;

/**
 * Maps startup failures to stable process exit codes so scripts and schedulers can react to them.
 *
 * <p>Spring Boot wraps exceptions thrown by an {@code ApplicationRunner}, so the whole cause chain is
 * inspected rather than just the top level exception.
 */
public final class StartupExitCodeMapper implements ExitCodeExceptionMapper {

    /** The environment is not usable: workspace or Blender validation failed. */
    public static final int ENVIRONMENT_NOT_READY = 2;

    /** Anything else. Matches Spring Boot's default failure exit code. */
    public static final int UNEXPECTED_FAILURE = 1;

    @Override
    public int getExitCode(Throwable exception) {
        // Identity set rather than a depth limit: cause chains can be self-referencing, and every
        // frame still needs to be inspected.
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = exception; current != null && visited.add(current); current = current.getCause()) {
            if (current instanceof StartupValidationException) {
                return ENVIRONMENT_NOT_READY;
            }
        }
        return UNEXPECTED_FAILURE;
    }
}
