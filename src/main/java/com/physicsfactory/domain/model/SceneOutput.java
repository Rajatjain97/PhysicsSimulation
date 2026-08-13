package com.physicsfactory.domain.model;

import com.physicsfactory.domain.exception.InvalidSceneContractException;
import java.util.Objects;

/**
 * Where Blender should put what it produces, as written into the scene contract.
 *
 * <p>Only the still image exists today. A video field is added here when MP4 rendering arrives; the
 * object wrapper is what makes that an addition rather than a breaking change to {@code output}.
 *
 * @param image workspace-relative path of the rendered PNG, forward slash separated
 */
public record SceneOutput(String image) {

    public SceneOutput {
        Objects.requireNonNull(image, "image must not be null");
        if (image.isBlank()) {
            throw new InvalidSceneContractException("output.image must not be blank.");
        }
    }
}
