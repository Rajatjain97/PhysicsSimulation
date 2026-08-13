package com.physicsfactory.application.port;

import com.physicsfactory.domain.exception.ScriptNotFoundException;
import java.nio.file.Path;
import java.util.List;

/**
 * Outbound port owning the {@code blender/scripts} directory.
 *
 * <p>Scripts ship inside the application and are installed into the workspace, so the running
 * application and the packaged jar always agree on what Blender executes.
 */
public interface BlenderScriptLibrary {

    /**
     * Copies every script bundled with the application into the render workspace, overwriting stale
     * copies. Idempotent, so it is safe to run on every start.
     *
     * @return the scripts that are now installed, in stable order
     */
    List<Path> installBundledScripts();

    /**
     * Resolves an installed script by file name, for example {@code healthcheck.py}.
     *
     * @throws ScriptNotFoundException if no such script is installed
     */
    Path locate(String scriptName);
}
