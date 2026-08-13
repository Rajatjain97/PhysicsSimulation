package com.physicsfactory.support;

import com.physicsfactory.application.port.SceneContractWriter;
import com.physicsfactory.domain.model.SceneContract;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** {@link SceneContractWriter} that remembers what it was asked to write instead of touching disk. */
public final class RecordingSceneContractWriter implements SceneContractWriter {

    private final List<Map.Entry<SceneContract, Path>> writes = new ArrayList<>();

    @Override
    public Path write(SceneContract contract, Path targetFile) {
        writes.add(Map.entry(contract, targetFile));
        return targetFile;
    }

    public List<Map.Entry<SceneContract, Path>> writes() {
        return List.copyOf(writes);
    }
}
