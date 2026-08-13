package com.physicsfactory.infrastructure.bootstrap;

import com.physicsfactory.application.usecase.DetectBlenderVersion;
import com.physicsfactory.application.usecase.RunBlenderHealthcheck;
import com.physicsfactory.domain.exception.BlenderExecutionException;
import com.physicsfactory.domain.model.BlenderVersion;
import com.physicsfactory.domain.model.RenderRequest;
import com.physicsfactory.domain.model.RenderResult;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Runs the Blender healthcheck when the application is started with {@code --healthcheck}, then lets
 * the application continue.
 *
 * <p>Gives operators and CI a single command that answers "is this machine able to render?" without
 * needing the test sources. Runs after {@link EnvironmentBootstrapRunner}, which is what installs the
 * script it executes.
 */
public final class BlenderHealthcheckRunner implements ApplicationRunner {

    /** Command line option that triggers the healthcheck: {@code --healthcheck}. */
    public static final String HEALTHCHECK_OPTION = "healthcheck";

    private static final Logger log = LoggerFactory.getLogger(BlenderHealthcheckRunner.class);

    private final DetectBlenderVersion detectBlenderVersion;
    private final RunBlenderHealthcheck runBlenderHealthcheck;
    private final RenderRequest healthcheckRequest;

    public BlenderHealthcheckRunner(DetectBlenderVersion detectBlenderVersion,
                                    RunBlenderHealthcheck runBlenderHealthcheck,
                                    RenderRequest healthcheckRequest) {
        this.detectBlenderVersion = Objects.requireNonNull(detectBlenderVersion, "detectBlenderVersion must not be null");
        this.runBlenderHealthcheck = Objects.requireNonNull(runBlenderHealthcheck, "runBlenderHealthcheck must not be null");
        this.healthcheckRequest = Objects.requireNonNull(healthcheckRequest, "healthcheckRequest must not be null");
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(HEALTHCHECK_OPTION)) {
            return;
        }
        BlenderVersion version = detectBlenderVersion.execute();
        log.info("Blender version detected: {} ({})", version.shortVersion(), version.raw());

        RenderResult result = runBlenderHealthcheck.execute(healthcheckRequest);
        log.info("Blender healthcheck {} in {}ms", result.status(), result.execution().duration().toMillis());
        log.info("Blender reported:{}{}", System.lineSeparator(), result.execution().standardOutput().strip());

        if (!result.isSuccessful()) {
            throw new BlenderExecutionException(
                    "Blender healthcheck failed with exit code " + result.execution().exitCode() + ".",
                    result.execution());
        }
    }
}
