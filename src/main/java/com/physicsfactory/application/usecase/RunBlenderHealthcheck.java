package com.physicsfactory.application.usecase;

import com.physicsfactory.application.port.BlenderProcessRunner;
import com.physicsfactory.application.port.BlenderScriptLibrary;
import com.physicsfactory.application.port.SceneContractWriter;
import com.physicsfactory.domain.exception.BlenderTimeoutException;
import com.physicsfactory.domain.exception.ScriptNotFoundException;
import com.physicsfactory.domain.model.BlenderExecution;
import com.physicsfactory.domain.model.BlenderScriptRequest;
import com.physicsfactory.domain.model.RenderJob;
import com.physicsfactory.domain.model.RenderRequest;
import com.physicsfactory.domain.model.RenderResult;
import com.physicsfactory.domain.model.RenderWorkspace;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Runs one job end to end through the integration layer without rendering anything.
 *
 * <p>It walks the full path a real render will take - accept a request, give it an identity, write
 * its scene contract into the render cache, resolve the Blender script, run it, report a structured
 * result - which makes it the smoke test for a machine's Blender setup. The healthcheck script
 * deliberately produces no video, so the result carries no output file.
 *
 * <p>When rendering arrives, the difference will be the template being executed, not this sequence.
 */
public final class RunBlenderHealthcheck {

    private final BlenderScriptLibrary scriptLibrary;
    private final SceneContractWriter sceneContractWriter;
    private final BlenderProcessRunner processRunner;
    private final RenderWorkspace renderWorkspace;

    public RunBlenderHealthcheck(BlenderScriptLibrary scriptLibrary,
                                 SceneContractWriter sceneContractWriter,
                                 BlenderProcessRunner processRunner,
                                 RenderWorkspace renderWorkspace) {
        this.scriptLibrary = Objects.requireNonNull(scriptLibrary, "scriptLibrary must not be null");
        this.sceneContractWriter = Objects.requireNonNull(sceneContractWriter, "sceneContractWriter must not be null");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner must not be null");
        this.renderWorkspace = Objects.requireNonNull(renderWorkspace, "renderWorkspace must not be null");
    }

    /**
     * @throws ScriptNotFoundException if the template's script is not installed
     * @throws BlenderTimeoutException if Blender outlived the request's timeout
     */
    public RenderResult execute(RenderRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        RenderJob job = RenderJob.create(request);
        Path sceneFile = renderWorkspace.cache().resolve(job.sceneFileName());
        sceneContractWriter.write(job.scene(), sceneFile);

        Path script = scriptLibrary.locate(request.scriptName());
        BlenderExecution execution = processRunner.runScript(BlenderScriptRequest.of(script, request.timeout()));

        return execution.isSuccessful()
                ? RenderResult.succeeded(job.id(), execution)
                : RenderResult.failed(job.id(), execution);
    }
}
