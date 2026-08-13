package com.physicsfactory.infrastructure.blender;

import com.physicsfactory.application.port.BlenderTemplateLibrary;
import com.physicsfactory.domain.exception.TemplateNotFoundException;
import com.physicsfactory.domain.model.RenderWorkspace;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Owns {@code blender/templates} in the render workspace.
 *
 * <p>Mirrors {@link ClasspathBlenderScriptLibrary}: templates ship with the application and are
 * materialised into the workspace so Blender can open a real file. They are installed on first use
 * rather than at startup, because a template is only needed by a render.
 *
 * <p>A {@code .blend} is preferred over a {@code .py} of the same name. Today the default template is
 * a Python builder, which stays reviewable in a pull request; the day somebody saves a real
 * {@code default.blend} next to it, it wins automatically and no Java changes.
 */
public final class ClasspathBlenderTemplateLibrary implements BlenderTemplateLibrary {

    private static final Logger log = LoggerFactory.getLogger(ClasspathBlenderTemplateLibrary.class);

    private static final String BUNDLED_TEMPLATE_LOCATION = ResourceLoader.CLASSPATH_URL_PREFIX + "blender/templates/";
    private static final List<String> TEMPLATE_EXTENSIONS = List.of(".blend", ".py");

    private final Path templatesDirectory;
    private final ResourceLoader resourceLoader;

    public ClasspathBlenderTemplateLibrary(RenderWorkspace renderWorkspace, ResourceLoader resourceLoader) {
        this.templatesDirectory = Objects.requireNonNull(renderWorkspace, "renderWorkspace must not be null").templates();
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
    }

    @Override
    public Path locate(String templateName) {
        Objects.requireNonNull(templateName, "templateName must not be null");
        try {
            Files.createDirectories(templatesDirectory);
            for (String extension : TEMPLATE_EXTENSIONS) {
                Path candidate = resolveInsideTemplates(templateName + extension);
                Resource bundled = resourceLoader.getResource(BUNDLED_TEMPLATE_LOCATION + templateName + extension);
                if (bundled.exists()) {
                    return install(bundled, candidate);
                }
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
            throw new TemplateNotFoundException(templateName, templatesDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not install the Blender template '" + templateName + "' into "
                    + templatesDirectory, e);
        }
    }

    /** Rejects names that contain path segments, so a template name can never escape the directory. */
    private Path resolveInsideTemplates(String fileName) {
        Path candidate = templatesDirectory.resolve(fileName).normalize();
        if (!templatesDirectory.equals(candidate.getParent())) {
            throw new TemplateNotFoundException(fileName, templatesDirectory);
        }
        return candidate;
    }

    private Path install(Resource bundled, Path target) throws IOException {
        byte[] contents = readBytes(bundled);
        if (Files.isRegularFile(target) && Arrays.equals(contents, Files.readAllBytes(target))) {
            log.debug("Blender template already up to date: {}", target);
            return target;
        }
        Files.write(target, contents, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        log.info("Installed Blender template {}", target);
        return target;
    }

    private static byte[] readBytes(Resource resource) throws IOException {
        try (InputStream stream = resource.getInputStream()) {
            return stream.readAllBytes();
        }
    }
}
