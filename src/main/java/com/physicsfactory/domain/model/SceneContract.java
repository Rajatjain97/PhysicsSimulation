package com.physicsfactory.domain.model;

import com.physicsfactory.domain.exception.InvalidSceneContractException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * The JSON document Java writes and Blender reads.
 *
 * <pre>
 * {
 *   "schemaVersion": "1.0",
 *   "template": "DefaultSphere",
 *   "parameters": {},
 *   "output": { "image": "output/renders/demo.png" }
 * }
 * </pre>
 *
 * <p>Three rules make this contract survivable over many months. {@code schemaVersion} is written on
 * every document, so a Blender template can refuse input it does not understand instead of guessing.
 * Every path is relative to the workspace root, written with forward slashes, because Blender is
 * launched with the workspace root as its working directory. And the contract says <em>what</em> to
 * render, never <em>how</em>: the template named here owns the camera, the lighting, the materials and
 * every object in the scene.
 *
 * <p>{@code parameters} is the extension point for template-specific input - marble count, palette,
 * seed. Java never interprets it; it is carried through to the template verbatim.
 *
 * @param schemaVersion contract version this document was written with
 * @param template      name of the Blender template that builds and renders the scene
 * @param parameters    template-specific input, passed through untouched
 * @param output        where Blender should put what it produces
 */
public record SceneContract(String schemaVersion, String template, Map<String, Object> parameters, SceneOutput output) {

    /** The contract version this build of Physics Reel Studio writes. */
    public static final String CURRENT_VERSION = "1.0";

    public SceneContract {
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(template, "template must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        Objects.requireNonNull(output, "output must not be null");
        if (schemaVersion.isBlank()) {
            throw new InvalidSceneContractException("schemaVersion must not be blank.");
        }
        if (template.isBlank()) {
            throw new InvalidSceneContractException("template must not be blank.");
        }
        parameters = Map.copyOf(parameters);
    }

    /** Builds the contract for a render request with no template parameters. */
    public static SceneContract forRequest(RenderRequest request) {
        return forRequest(request, Map.of());
    }

    /** Builds the contract for a render request, at the current contract version. */
    public static SceneContract forRequest(RenderRequest request, Map<String, ?> parameters) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        return new SceneContract(CURRENT_VERSION, request.template(), Map.copyOf(parameters),
                new SceneOutput(toPortablePath(request.outputFile())));
    }

    private static String toPortablePath(Path path) {
        return StreamSupport.stream(path.spliterator(), false)
                .map(Path::toString)
                .collect(Collectors.joining("/"));
    }
}
