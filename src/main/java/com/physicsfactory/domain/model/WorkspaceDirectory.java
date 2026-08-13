package com.physicsfactory.domain.model;

import java.util.Locale;

/**
 * The directories Physics Factory needs on disk.
 *
 * <p>The enum is the single source of truth for "which directories exist". Adding a directory for a
 * future story is a two line change: add a constant here and a mapping under
 * {@code physics-factory.workspace.directories} in {@code application.yaml}. Startup then fails fast
 * if the mapping is missing, so the two can never silently drift apart.
 */
public enum WorkspaceDirectory {

    /** Source assets used by scene generation (HDRIs, textures, blend fragments). */
    ASSETS,

    /** Scene and pipeline configuration files that are read at runtime. */
    CONFIGS,

    /** Final rendered videos. */
    VIDEO_OUTPUT,

    /** Thumbnails extracted from, or generated for, rendered videos. */
    THUMBNAIL_OUTPUT,

    /** Application log files. */
    LOGS;

    /**
     * The relaxed, kebab-case key used for this directory in configuration files, for example
     * {@code video-output}.
     */
    public String configKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
