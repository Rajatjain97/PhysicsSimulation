package com.physicsfactory.application.port;

import com.physicsfactory.domain.exception.TemplateNotFoundException;
import java.nio.file.Path;

/**
 * Outbound port owning the {@code blender/templates} directory.
 *
 * <p>Sibling of {@link BlenderScriptLibrary}: templates ship with the application and are materialised
 * into the workspace, so Blender - which cannot read a jar - always gets a real file.
 */
public interface BlenderTemplateLibrary {

    /**
     * Resolves a template by name, installing the bundled copy into the workspace if it is missing or
     * out of date.
     *
     * @param templateName template name without extension, for example {@code default}
     * @throws TemplateNotFoundException if no such template ships with the application and none is
     *                                   present in the workspace
     */
    Path locate(String templateName);
}
