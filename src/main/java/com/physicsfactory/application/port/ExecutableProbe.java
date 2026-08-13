package com.physicsfactory.application.port;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Outbound port for locating an executable program on the host machine.
 *
 * <p>Keeping the "how" (absolute path? relative path? search the {@code PATH}? platform specific
 * suffixes?) behind this port is what allows the Blender validation use case to stay free of
 * operating system details.
 */
public interface ExecutableProbe {

    /**
     * Resolves a configured program location to an absolute path.
     *
     * @param configuredLocation an absolute path, a path relative to the working directory, or a bare
     *                           program name to be looked up on the system search path
     * @return the resolved absolute path, or {@link Optional#empty()} if nothing executable was found
     */
    Optional<Path> resolve(String configuredLocation);
}
