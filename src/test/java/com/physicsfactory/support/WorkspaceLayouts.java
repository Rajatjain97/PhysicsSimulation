package com.physicsfactory.support;

import com.physicsfactory.domain.model.WorkspaceDirectory;
import com.physicsfactory.domain.model.WorkspaceLayout;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/** Builds the layout that {@code application.yaml} describes, for use in tests. */
public final class WorkspaceLayouts {

    private WorkspaceLayouts() {
    }

    public static Map<WorkspaceDirectory, String> defaultRelativePaths() {
        Map<WorkspaceDirectory, String> paths = new EnumMap<>(WorkspaceDirectory.class);
        paths.put(WorkspaceDirectory.ASSETS, "assets");
        paths.put(WorkspaceDirectory.CONFIGS, "configs");
        paths.put(WorkspaceDirectory.VIDEO_OUTPUT, "output/videos");
        paths.put(WorkspaceDirectory.THUMBNAIL_OUTPUT, "output/thumbnails");
        paths.put(WorkspaceDirectory.LOGS, "logs");
        paths.put(WorkspaceDirectory.BLENDER_SCRIPTS, "blender/scripts");
        paths.put(WorkspaceDirectory.BLENDER_TEMPLATES, "blender/templates");
        paths.put(WorkspaceDirectory.BLENDER_RENDERS, "blender/renders");
        paths.put(WorkspaceDirectory.BLENDER_CACHE, "blender/cache");
        return paths;
    }

    public static WorkspaceLayout rootedAt(Path root) {
        return WorkspaceLayout.of(root, defaultRelativePaths());
    }
}
