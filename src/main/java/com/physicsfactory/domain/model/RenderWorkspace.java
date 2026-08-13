package com.physicsfactory.domain.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The part of the workspace that belongs to Blender.
 *
 * <p>Deliberately separate from the application output folders: {@code output/videos},
 * {@code output/thumbnails} and {@code output/renders} hold deliverables the user cares about, while
 * everything here is the render engine's working area and can be deleted without losing anything.
 *
 * <p>It is a narrow view over {@link WorkspaceLayout} so render code can say
 * {@code renderWorkspace.templates()} instead of remembering which enum constant to look up.
 *
 * @param scripts   Blender Python entry points, installed from the classpath
 * @param engine    the Blender-side rendering engine modules
 * @param templates template modules, one directory per template
 * @param renders   Blender's own render output, before it becomes a deliverable
 * @param cache     scene contracts and other short-lived files handed to Blender
 */
public record RenderWorkspace(Path scripts, Path engine, Path templates, Path renders, Path cache) {

    public RenderWorkspace {
        Objects.requireNonNull(scripts, "scripts must not be null");
        Objects.requireNonNull(engine, "engine must not be null");
        Objects.requireNonNull(templates, "templates must not be null");
        Objects.requireNonNull(renders, "renders must not be null");
        Objects.requireNonNull(cache, "cache must not be null");
    }

    public static RenderWorkspace of(WorkspaceLayout layout) {
        Objects.requireNonNull(layout, "layout must not be null");
        return new RenderWorkspace(layout.pathOf(WorkspaceDirectory.BLENDER_SCRIPTS),
                layout.pathOf(WorkspaceDirectory.BLENDER_ENGINE),
                layout.pathOf(WorkspaceDirectory.BLENDER_TEMPLATES),
                layout.pathOf(WorkspaceDirectory.BLENDER_RENDERS),
                layout.pathOf(WorkspaceDirectory.BLENDER_CACHE));
    }

    /** All directories, in declaration order. */
    public List<Path> directories() {
        return List.of(scripts, engine, templates, renders, cache);
    }
}
