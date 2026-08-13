package com.physicsfactory.domain.model;

import com.physicsfactory.domain.exception.InvalidSceneContractException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * The JSON document Java writes and Blender reads.
 *
 * <pre>
 * {
 *   "sceneVersion": 1,
 *   "template": "healthcheck",
 *   "output": "output/videos/demo.mp4"
 * }
 * </pre>
 *
 * <p>Two rules make this contract survivable over many months. {@code sceneVersion} is written on
 * every document, so a Blender script can refuse input it does not understand instead of guessing.
 * And {@code output} is always relative to the workspace root, written with forward slashes: Blender
 * is launched with the workspace root as its working directory, so the same contract works on every
 * operating system and can be moved between machines.
 *
 * <p>Fields are added, never renamed or removed. A breaking change means a new
 * {@link #CURRENT_VERSION}.
 *
 * @param sceneVersion contract version this document was written with
 * @param template     name of the Blender template that should render the scene
 * @param output       workspace-relative output path, forward slash separated
 */
public record SceneContract(int sceneVersion, String template, String output) {

    /** The contract version this build of Physics Reel Studio writes. */
    public static final int CURRENT_VERSION = 1;

    public SceneContract {
        Objects.requireNonNull(template, "template must not be null");
        Objects.requireNonNull(output, "output must not be null");
        if (sceneVersion < 1) {
            throw new InvalidSceneContractException("sceneVersion must be at least 1 but was " + sceneVersion + ".");
        }
        if (template.isBlank()) {
            throw new InvalidSceneContractException("template must not be blank.");
        }
        if (output.isBlank()) {
            throw new InvalidSceneContractException("output must not be blank.");
        }
    }

    /** Builds the contract for a render request, at the current contract version. */
    public static SceneContract forRequest(RenderRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return new SceneContract(CURRENT_VERSION, request.template(), toPortablePath(request.outputFile()));
    }

    private static String toPortablePath(Path path) {
        return StreamSupport.stream(path.spliterator(), false)
                .map(Path::toString)
                .collect(Collectors.joining("/"));
    }
}
