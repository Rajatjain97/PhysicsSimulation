package com.physicsfactory.application.port;

import java.nio.file.Path;
import java.util.List;

/**
 * Outbound port that materialises the Blender-side runtime - the rendering engine and the template
 * modules - into the render workspace.
 *
 * <p>Sibling of {@link BlenderScriptLibrary}: both exist because Blender cannot read a jar, so
 * everything it executes has to become a real file first. Java installs those files and nothing more;
 * which templates exist and how they are resolved is decided inside Blender by the template registry.
 */
public interface BlenderRuntimeLibrary {

    /**
     * Copies the bundled engine modules and template modules into the workspace, replacing stale
     * copies. Idempotent, so it is safe to call before every render.
     *
     * @return the files that are now installed, in stable order
     */
    List<Path> installRuntime();
}
