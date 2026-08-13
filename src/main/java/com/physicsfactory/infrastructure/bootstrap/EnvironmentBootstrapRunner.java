package com.physicsfactory.infrastructure.bootstrap;

import com.physicsfactory.application.usecase.BootstrapEnvironment;
import com.physicsfactory.application.usecase.BootstrapRequest;
import com.physicsfactory.domain.model.WorkspaceLayout;
import java.util.Objects;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Bridges the Spring Boot lifecycle to the {@link BootstrapEnvironment} use case.
 *
 * <p>This is deliberately the only place where framework lifecycle and business logic meet. A
 * {@code StartupValidationException} thrown from here aborts the run; the exception is then rendered
 * by {@code StartupValidationFailureAnalyzer} and translated into a process exit code by
 * {@code StartupExitCodeMapper}.
 */
public final class EnvironmentBootstrapRunner implements ApplicationRunner {

    private final BootstrapEnvironment bootstrapEnvironment;
    private final WorkspaceLayout workspaceLayout;
    private final String blenderExecutableLocation;

    public EnvironmentBootstrapRunner(BootstrapEnvironment bootstrapEnvironment,
                                      WorkspaceLayout workspaceLayout,
                                      String blenderExecutableLocation) {
        this.bootstrapEnvironment = Objects.requireNonNull(bootstrapEnvironment, "bootstrapEnvironment must not be null");
        this.workspaceLayout = Objects.requireNonNull(workspaceLayout, "workspaceLayout must not be null");
        this.blenderExecutableLocation =
                Objects.requireNonNull(blenderExecutableLocation, "blenderExecutableLocation must not be null");
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrapEnvironment.execute(new BootstrapRequest(workspaceLayout, blenderExecutableLocation));
    }
}
