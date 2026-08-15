package com.physicsfactory.infrastructure.batch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.physicsfactory.application.port.BatchManifestWriter;
import com.physicsfactory.domain.model.BatchManifest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Writes the batch manifest as JSON.
 *
 * <p>Its own mapper, for the same reason the scene contract has one: a record of what was produced
 * should not change shape because something elsewhere reconfigured a shared mapper.
 */
public final class JacksonBatchManifestWriter implements BatchManifestWriter {

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.ALWAYS,
                    JsonInclude.Include.ALWAYS))
            .build();

    @Override
    public Path write(BatchManifest manifest, Path targetFile) {
        Objects.requireNonNull(manifest, "manifest must not be null");
        Objects.requireNonNull(targetFile, "targetFile must not be null");
        try {
            Path parent = targetFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            byte[] json = jsonMapper.writeValueAsBytes(manifest);
            Files.write(targetFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            return targetFile;
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Could not serialise the batch manifest for " + manifest.batchId(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the batch manifest to " + targetFile, e);
        }
    }
}
