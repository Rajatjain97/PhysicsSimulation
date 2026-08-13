package com.physicsfactory.application.usecase;

import com.physicsfactory.application.port.BlenderProcessRunner;
import com.physicsfactory.application.port.BlenderScriptLibrary;
import com.physicsfactory.application.port.BlenderTemplateLibrary;
import com.physicsfactory.application.port.SceneContractWriter;
import com.physicsfactory.domain.exception.RenderOutputMissingException;
import com.physicsfactory.domain.model.BlenderExecution;
import com.physicsfactory.domain.model.BlenderScriptRequest;
import com.physicsfactory.domain.model.RenderJob;
import com.physicsfactory.domain.model.RenderRequest;
import com.physicsfactory.domain.model.RenderResult;
import com.physicsfactory.domain.model.RenderWorkspace;
import com.physicsfactory.domain.model.SceneObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders one scene: build the contract, hand it to Blender, confirm the file arrived.
 *
 * <p>This is the whole pipeline Java is responsible for. Every step is an instruction; none of them
 * is rendering logic. What a sphere is, where the camera goes, how a material is built and how the
 * pixels are produced all live in Python, inside Blender.
 *
 * <p>The stages are logged here rather than in the caller because this is the only place that sees
 * them all, and "which step was it on when it failed" is the first question anyone asks.
 */
public final class RenderScene {

    /** The Blender script that renders a scene contract. */
    public static final String RENDER_SCRIPT = "render_scene.py";

    private static final Logger log = LoggerFactory.getLogger(RenderScene.class);

    private static final String SCENE_ARGUMENT = "--scene";
    private static final String TEMPLATE_ARGUMENT = "--template";

    private final BlenderTemplateLibrary templateLibrary;
    private final BlenderScriptLibrary scriptLibrary;
    private final SceneContractWriter sceneContractWriter;
    private final BlenderProcessRunner processRunner;
    private final RenderWorkspace renderWorkspace;
    private final Path workspaceRoot;

    public RenderScene(BlenderTemplateLibrary templateLibrary,
                       BlenderScriptLibrary scriptLibrary,
                       SceneContractWriter sceneContractWriter,
                       BlenderProcessRunner processRunner,
                       RenderWorkspace renderWorkspace,
                       Path workspaceRoot) {
        this.templateLibrary = Objects.requireNonNull(templateLibrary, "templateLibrary must not be null");
        this.scriptLibrary = Objects.requireNonNull(scriptLibrary, "scriptLibrary must not be null");
        this.sceneContractWriter = Objects.requireNonNull(sceneContractWriter, "sceneContractWriter must not be null");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner must not be null");
        this.renderWorkspace = Objects.requireNonNull(renderWorkspace, "renderWorkspace must not be null");
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
    }

    /**
     * @throws com.physicsfactory.domain.exception.TemplateNotFoundException  if the template is unknown
     * @throws com.physicsfactory.domain.exception.ScriptNotFoundException    if the render script is missing
     * @throws com.physicsfactory.domain.exception.InvalidSceneContractException if the contract cannot be written
     * @throws com.physicsfactory.domain.exception.BlenderTimeoutException    if Blender outlived the budget
     * @throws RenderOutputMissingException                                   if Blender succeeded but wrote nothing
     */
    public RenderResult execute(RenderRequest request, List<SceneObject> objects) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(objects, "objects must not be null");

        log.info("Generating scene contract for template '{}' with {} object(s)...",
                request.template(), objects.size());
        RenderJob job = RenderJob.create(request, objects);

        Path template = templateLibrary.locate(request.template());
        Path script = scriptLibrary.locate(RENDER_SCRIPT);

        Path sceneFile = renderWorkspace.cache().resolve(job.sceneFileName());
        log.info("Writing JSON to {}...", sceneFile);
        sceneContractWriter.write(job.scene(), sceneFile);

        log.info("Launching Blender with template {}...", template);
        BlenderExecution execution = processRunner.runScript(new BlenderScriptRequest(script,
                List.of(SCENE_ARGUMENT, sceneFile.toString(), TEMPLATE_ARGUMENT, template.toString()),
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
        log.info("Rendering completed successfully in {}ms", execution.duration().toMillis());
        return RenderResult.succeeded(job.id(), execution, image);
    }
}
