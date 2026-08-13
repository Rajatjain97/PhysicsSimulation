package com.physicsfactory.domain.exception;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Raised when the Blender template named by a render request cannot be resolved.
 */
public final class TemplateNotFoundException extends BlenderIntegrationException {

    private static final long serialVersionUID = 1L;

    private final String templateName;

    public TemplateNotFoundException(String templateName, Path templatesDirectory) {
        super("Blender template '" + templateName + "' was not found in " + templatesDirectory + ".",
                "Bundled templates live in src/main/resources/blender/templates and are installed into the "
                        + "render workspace on demand. Add '" + templateName + ".blend' or '" + templateName
                        + ".py' there, or set 'physics-factory.render.template' to a template that exists.");
        this.templateName = Objects.requireNonNull(templateName, "templateName must not be null");
    }

    /** The template that was requested, as the caller named it. */
    public String templateName() {
        return templateName;
    }
}
