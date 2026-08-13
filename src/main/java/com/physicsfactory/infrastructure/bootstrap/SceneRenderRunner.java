package com.physicsfactory.infrastructure.bootstrap;

import com.physicsfactory.application.usecase.RenderScene;
import com.physicsfactory.domain.exception.BlenderExecutionException;
import com.physicsfactory.domain.model.RenderRequest;
import com.physicsfactory.domain.model.RenderResult;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Renders the demo scene when the application is started with {@code --render}.
 *
 * <p>Renders whichever template {@code physics-factory.render.template} names. The runner knows
 * nothing about what that template builds - swapping {@code DefaultSphere} for a future
 * {@code MarbleArena} is a configuration change, not a code change.
 *
 * <p>Runs after {@link EnvironmentBootstrapRunner}, which installs the render script.
 */
public final class SceneRenderRunner implements ApplicationRunner {

    /** Command line option that triggers the render: {@code --render}. */
    public static final String RENDER_OPTION = "render";

    private static final Logger log = LoggerFactory.getLogger(SceneRenderRunner.class);

    private final RenderScene renderScene;
    private final RenderRequest renderRequest;
    private final Map<String, String> parameters;

    public SceneRenderRunner(RenderScene renderScene, RenderRequest renderRequest, Map<String, String> parameters) {
        this.renderScene = Objects.requireNonNull(renderScene, "renderScene must not be null");
        this.renderRequest = Objects.requireNonNull(renderRequest, "renderRequest must not be null");
        this.parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters must not be null"));
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(RENDER_OPTION)) {
            return;
        }
        log.info("Rendering template '{}' with parameters {}...", renderRequest.template(), parameters);
        RenderResult result = renderScene.execute(renderRequest, parameters);

        if (!result.isSuccessful()) {
            throw new BlenderExecutionException(
                    "Blender render failed with exit code " + result.execution().exitCode() + ".",
                    result.execution());
        }
    }
}
