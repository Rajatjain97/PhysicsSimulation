package com.physicsfactory.application.port;

import com.physicsfactory.domain.exception.InvalidSceneContractException;
import com.physicsfactory.domain.model.SceneContract;
import java.nio.file.Path;

/**
 * Outbound port that materialises a {@link SceneContract} as the JSON file Blender reads.
 *
 * <p>Java serialises, Blender consumes: this port is the only place a scene contract becomes bytes.
 */
public interface SceneContractWriter {

    /**
     * Writes the contract to {@code targetFile}, creating parent directories as needed and replacing
     * any existing file.
     *
     * @return {@code targetFile}, for chaining
     * @throws InvalidSceneContractException if the contract cannot be serialised
     * @throws java.io.UncheckedIOException  if the file cannot be written
     */
    Path write(SceneContract contract, Path targetFile);
}
