package com.physicsfactory.application.port;

import com.physicsfactory.domain.exception.BlenderExecutionException;
import com.physicsfactory.domain.exception.BlenderTimeoutException;
import com.physicsfactory.domain.exception.ScriptNotFoundException;
import com.physicsfactory.domain.model.BlenderExecution;
import com.physicsfactory.domain.model.BlenderScriptRequest;
import java.time.Duration;

/**
 * Outbound port for running Blender as an external process.
 *
 * <p>The two methods are the only ways Java is allowed to reach Blender. Both return the full
 * {@link BlenderExecution} - including a non-zero exit code - because deciding what counts as failure
 * is the caller's job, not the runner's.
 */
public interface BlenderProcessRunner {

    /**
     * Runs {@code blender --version}.
     *
     * @throws BlenderExecutionException if Blender could not be started
     * @throws BlenderTimeoutException   if the probe outlived {@code timeout}
     */
    BlenderExecution runVersionProbe(Duration timeout);

    /**
     * Runs a Python script inside Blender in background mode.
     *
     * @throws ScriptNotFoundException   if the script does not exist
     * @throws BlenderExecutionException if Blender could not be started
     * @throws BlenderTimeoutException   if the script outlived its timeout
     */
    BlenderExecution runScript(BlenderScriptRequest request);
}
