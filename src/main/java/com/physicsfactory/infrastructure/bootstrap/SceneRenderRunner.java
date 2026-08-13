package com.physicsfactory.infrastructure.bootstrap;

import com.physicsfactory.application.usecase.RenderScene;
import com.physicsfactory.domain.exception.BlenderExecutionException;
import com.physicsfactory.domain.model.RenderRequest;
import com.physicsfactory.domain.model.RenderResult;
import com.physicsfactory.domain.model.SceneObject;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Renders the demo scene when the application is started with {@code --render}.
 *
 * <p>The first end-to-end proof that the pipeline works: one sphere, one PNG. The scene is described
 * here rather than in configuration because it is a fixed demonstration, not something an operator
 * tunes; when real scene generation arrives it replaces this runner rather than extending it.
 *
 * <p>Runs after {@link EnvironmentBootstrapRunner}, which installs the render script.
 */
public final class SceneRenderRunner implements ApplicationRunner {

    /** Command line option that triggers the render: {@code --render}. */
    public static final String RENDER_OPTION = "render";

    private static final Logger log = LoggerFactory.getLogger(SceneRenderRunner.class);

    private final RenderScene renderScene;
    private final RenderRequest renderRequest;

    public SceneRenderRunner(RenderScene renderScene, RenderRequest renderRequest) {
        this.renderScene = Objects.requireNonNull(renderScene, "renderScene must not be null");
        this.renderRequest = Objects.requireNonNull(renderRequest, "renderRequest must not be null");
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(RENDER_OPTION)) {
            return;
        }
        log.info("Rendering...");
        RenderResult result = renderScene.execute(renderRequest, List.of(SceneObject.sphereAtOrigin()));

        if (!result.isSuccessful()) {
            throw new BlenderExecutionException(
                    "Blender render failed with exit code " + result.execution().exitCode() + ".",
                    result.execution());
        }
    }
}
