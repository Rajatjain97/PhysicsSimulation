package com.physicsfactory.domain.model;

import com.physicsfactory.domain.exception.InvalidSceneContractException;
import java.util.Locale;
import java.util.Objects;

/**
 * Where Blender should put what it produces, as written into the scene contract.
 *
 * <p>Exactly one of the two is set. Which one depends on the file the render request asked for, and
 * it tells Blender - and later a reader of the manifest - what kind of artefact this render was
 * meant to produce. A template decides whether it renders a still or a movie by declaring a
 * duration; this records what Java is going to look for afterwards.
 *
 * @param image workspace-relative path of a rendered still, forward slash separated
 * @param video workspace-relative path of a rendered movie, forward slash separated
 */
public record SceneOutput(String image, String video) {

    private static final String VIDEO_EXTENSION = ".mp4";

    public SceneOutput {
        boolean hasImage = image != null && !image.isBlank();
        boolean hasVideo = video != null && !video.isBlank();
        if (!hasImage && !hasVideo) {
            throw new InvalidSceneContractException("output must name either output.image or output.video.");
        }
    }

    /** A still image output. */
    public SceneOutput(String image) {
        this(image, null);
    }

    /** Chooses the output kind from the file extension. */
    public static SceneOutput forFile(String portablePath) {
        Objects.requireNonNull(portablePath, "portablePath must not be null");
        if (portablePath.toLowerCase(Locale.ROOT).endsWith(VIDEO_EXTENSION)) {
            return new SceneOutput(null, portablePath);
        }
        return new SceneOutput(portablePath, null);
    }

    /** The path that was requested, whichever kind it is. */
    public String path() {
        return (video != null && !video.isBlank()) ? video : image;
    }
}
