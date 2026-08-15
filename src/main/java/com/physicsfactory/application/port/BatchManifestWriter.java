package com.physicsfactory.application.port;

import com.physicsfactory.domain.model.BatchManifest;
import java.nio.file.Path;

/**
 * Outbound port that records a batch beside its videos.
 *
 * <p>Sibling of {@link SceneContractWriter}: one writes what Blender should do, this writes what a
 * batch actually did.
 */
public interface BatchManifestWriter {

    /**
     * @return {@code targetFile}, for chaining
     * @throws java.io.UncheckedIOException if the manifest cannot be written
     */
    Path write(BatchManifest manifest, Path targetFile);
}
