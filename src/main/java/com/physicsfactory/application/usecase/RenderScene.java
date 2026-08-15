package com.physicsfactory.application.usecase;

import com.physicsfactory.application.port.BlenderProcessRunner;
import com.physicsfactory.application.port.BlenderRuntimeLibrary;
import com.physicsfactory.application.port.BlenderScriptLibrary;
import com.physicsfactory.application.port.SceneContractWriter;
import com.physicsfactory.domain.exception.RenderOutputMissingException;
import com.physicsfactory.domain.model.BlenderExecution;
import com.physicsfactory.domain.model.BlenderScriptRequest;
import com.physicsfactory.domain.model.RenderJob;
import com.physicsfactory.domain.model.RenderRequest;
import com.physicsfactory.domain.model.RenderResult;
import com.physicsfactory.domain.model.RenderWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders one scene: build the contract, hand it to Blender, confirm the file arrived.
 *
 * <p>This is the whole pipeline Java is responsible for, and it is template-agnostic by design. Java
 * names a template and passes the directory templates live in; which template that name resolves to,
 * and what it builds, is decided by the registry inside Blender. Adding {@code MarbleArena} therefore
 * changes nothing here.
 *
 * <p>The stages are logged here because this is the only place that sees them all, and "which step
 * was it on when it failed" is the first question anyone asks.
 */
public final class RenderScene {

    /** The Blender entry point that renders a scene contract. */
    public static final String RENDER_SCRIPT = "render_scene.py";

    private static final Logger log = LoggerFactory.getLogger(RenderScene.class);

    /** Contract parameter carrying the render seed. */
    public static final String SEED_PARAMETER = "seed";

    /** Keeps a generated seed small enough to read in a log and retype by hand. */
    private static final long MAX_SEED = 1_000_000_000L;

    private static final String SCENE_ARGUMENT = "--scene";
    private static final String ENGINE_ARGUMENT = "--engine";
    private static final String ASSETS_ARGUMENT = "--assets";
    private static final String TEMPLATES_ARGUMENT = "--templates";
    private static final String RENDER_ID_ARGUMENT = "--render-id";
    private static final String QUALITY_ARGUMENT = "--quality";

    private final BlenderRuntimeLibrary runtimeLibrary;
    private final BlenderScriptLibrary scriptLibrary;
    private final SceneContractWriter sceneContractWriter;
    private final BlenderProcessRunner processRunner;
    private final RenderWorkspace renderWorkspace;
    private final Path workspaceRoot;

    public RenderScene(BlenderRuntimeLibrary runtimeLibrary,
                       BlenderScriptLibrary scriptLibrary,
                       SceneContractWriter sceneContractWriter,
                       BlenderProcessRunner processRunner,
                       RenderWorkspace renderWorkspace,
                       Path workspaceRoot) {
        this.runtimeLibrary = Objects.requireNonNull(runtimeLibrary, "runtimeLibrary must not be null");
        this.scriptLibrary = Objects.requireNonNull(scriptLibrary, "scriptLibrary must not be null");
        this.sceneContractWriter = Objects.requireNonNull(sceneContractWriter, "sceneContractWriter must not be null");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner must not be null");
        this.renderWorkspace = Objects.requireNonNull(renderWorkspace, "renderWorkspace must not be null");
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
    }

    /**
     * Guarantees the render has a seed.
     *
     * <p>Every random choice a template makes comes from this one number, so a render without a seed
     * could never be reproduced. An operator's seed is used exactly as given; otherwise one is
     * derived from the job's own id - unique per render, and recorded in the manifest along with
     * every other parameter, which is what makes a reel rebuildable from its manifest alone.
     */
    private static Map<String, Object> seeded(Map<String, ?> parameters) {
        Map<String, Object> seededParameters = new LinkedHashMap<>(parameters);
        seededParameters.computeIfAbsent(SEED_PARAMETER,
                key -> Math.abs(UUID.randomUUID().getMostSignificantBits() % MAX_SEED));
        return seededParameters;
    }

    /**
     * @throws com.physicsfactory.domain.exception.ScriptNotFoundException    if the render script is missing
     * @throws com.physicsfactory.domain.exception.InvalidSceneContractException if the contract cannot be written
     * @throws com.physicsfactory.domain.exception.BlenderTimeoutException    if Blender outlived the budget
     * @throws RenderOutputMissingException                                   if Blender succeeded but wrote nothing
     */
    public RenderResult execute(RenderRequest request) {
        return execute(request, Map.of());
    }

    /**
     * @param parameters template-specific input, carried into the scene contract untouched
     */
    public RenderResult execute(RenderRequest request, Map<String, ?> parameters) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");

        log.info("Installing Blender runtime...");
        runtimeLibrary.installRuntime();

        RenderJob job = RenderJob.create(request, seeded(parameters));
        log.info("Generating scene contract for template '{}' with seed {}...",
                request.template(), job.scene().parameters().get(SEED_PARAMETER));
        Path script = scriptLibrary.locate(RENDER_SCRIPT);

        Path sceneFile = renderWorkspace.cache().resolve(job.sceneFileName());
        log.info("Writing JSON to {}...", sceneFile);
        sceneContractWriter.write(job.scene(), sceneFile);

        log.info("Launching Blender at {} quality...", request.quality());
        BlenderExecution execution = processRunner.runScript(new BlenderScriptRequest(script,
                List.of(SCENE_ARGUMENT, sceneFile.toString(),
                        ENGINE_ARGUMENT, renderWorkspace.engine().toString(),
                        ASSETS_ARGUMENT, renderWorkspace.assets().toString(),
                        TEMPLATES_ARGUMENT, renderWorkspace.templates().toString(),
                        RENDER_ID_ARGUMENT, job.id().toString(),
                        QUALITY_ARGUMENT, request.quality().argument()),
                request.timeout()));

        if (!execution.isSuccessful()) {
            log.error("Rendering failed | exitCode={} | stderr={}", execution.exitCode(),
                    execution.standardError().isBlank() ? "(empty)" : execution.standardError().strip());
            return RenderResult.failed(job.id(), execution);
        }

        Path image = workspaceRoot.resolve(request.outputFile());
        if (!Files.isRegularFile(image)) {
            throw new RenderOutputMissingException(image, execution.commandLine());
        }
        log.info("Output saved: {}", image);
        // The Blender side prints how long each of its own stages took. This is the whole process,
        // so the difference between the two is Blender's startup and teardown.
        log.info("Rendering completed successfully in {}ms (whole Blender process, {} quality)",
                execution.duration().toMillis(), request.quality());
        return RenderResult.succeeded(job.id(), execution, image);
    }
}
