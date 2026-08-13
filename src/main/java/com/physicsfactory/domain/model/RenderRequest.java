package com.physicsfactory.domain.model;

import com.physicsfactory.domain.exception.InvalidSceneContractException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What the user asked for: a template, a place to put the result, and a time budget.
 *
 * <p>The template name doubles as the name of the Blender script that renders it, so it is validated
 * strictly - a template can never smuggle a path segment into the script lookup.
 *
 * @param template   template name, for example {@code healthcheck}
 * @param outputFile where the finished video belongs, relative to the workspace root
 * @param timeout    how long Blender may take before the invocation is killed
 */
public record RenderRequest(String template, Path outputFile, Duration timeout) {

    private static final Pattern TEMPLATE_NAME = Pattern.compile("[a-z0-9]+(?:[-_][a-z0-9]+)*");

    public RenderRequest {
        Objects.requireNonNull(template, "template must not be null");
        Objects.requireNonNull(outputFile, "outputFile must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (!TEMPLATE_NAME.matcher(template).matches()) {
            throw new InvalidSceneContractException("Template name '" + template
                    + "' is invalid: use lower case letters, digits, '-' and '_' only.");
        }
        if (outputFile.isAbsolute()) {
            throw new InvalidSceneContractException("Output file '" + outputFile
                    + "' must be relative to the workspace root.");
        }
        if (outputFile.normalize().startsWith("..")) {
            throw new InvalidSceneContractException("Output file '" + outputFile
                    + "' must stay inside the workspace root.");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new InvalidSceneContractException("Timeout must be positive but was " + timeout + ".");
        }
    }

    /** The Blender script that renders this template. */
    public String scriptName() {
        return template + ".py";
    }
}
