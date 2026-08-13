package com.physicsfactory.domain.model;

import com.physicsfactory.domain.exception.InvalidSceneContractException;
import java.util.List;
import java.util.Objects;

/**
 * One object Blender should place in the scene.
 *
 * <p>Intentionally thin: a type name Blender knows how to build and where to put it. Materials,
 * scale, rotation and physics are fields added later - Java stays a courier and never learns what a
 * sphere actually is.
 *
 * @param type     object type Blender understands, for example {@code sphere}
 * @param location x, y and z in Blender world space
 */
public record SceneObject(String type, List<Double> location) {

    private static final int COORDINATES = 3;

    public SceneObject {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(location, "location must not be null");
        if (type.isBlank()) {
            throw new InvalidSceneContractException("Object type must not be blank.");
        }
        if (location.size() != COORDINATES) {
            throw new InvalidSceneContractException(
                    "Object location must have exactly " + COORDINATES + " coordinates but had " + location.size() + ".");
        }
        if (location.contains(null)) {
            throw new InvalidSceneContractException("Object location must not contain null coordinates.");
        }
        location = List.copyOf(location);
    }

    /** The single object the first end-to-end render places. */
    public static SceneObject sphereAtOrigin() {
        return new SceneObject("sphere", List.of(0.0, 0.0, 0.0));
    }
}
