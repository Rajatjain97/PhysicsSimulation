package com.physicsfactory.infrastructure.blender;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.physicsfactory.application.port.SceneContractWriter;
import com.physicsfactory.domain.exception.InvalidSceneContractException;
import com.physicsfactory.domain.model.SceneContract;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes scene contracts as JSON.
 *
 * <p>The mapper is created and configured here rather than injected. A shared, application-wide
 * {@code ObjectMapper} is tuned for whatever else the application serialises and can be reconfigured
 * by a future dependency; the scene contract is an agreement with Blender and must serialise the same
 * way in every release. Output is indented because these files are read by humans while debugging a
 * template.
 */
public final class JacksonSceneContractWriter implements SceneContractWriter {

    private static final Logger log = LoggerFactory.getLogger(JacksonSceneContractWriter.class);

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    @Override
    public Path write(SceneContract contract, Path targetFile) {
        Objects.requireNonNull(contract, "contract must not be null");
        Objects.requireNonNull(targetFile, "targetFile must not be null");
        try {
            Path parent = targetFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            byte[] json = jsonMapper.writeValueAsBytes(contract);
            Files.write(targetFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            log.debug("Wrote scene contract v{} for template '{}' to {}",
                    contract.sceneVersion(), contract.template(), targetFile);
            return targetFile;
        } catch (JsonProcessingException e) {
            throw new InvalidSceneContractException(
                    "Could not serialise the scene contract for template '" + contract.template() + "'.", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the scene contract to " + targetFile, e);
        }
    }
}
