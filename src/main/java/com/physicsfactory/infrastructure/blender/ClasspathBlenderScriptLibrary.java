package com.physicsfactory.infrastructure.blender;

import com.physicsfactory.application.port.BlenderScriptLibrary;
import com.physicsfactory.domain.exception.ScriptNotFoundException;
import com.physicsfactory.domain.model.RenderWorkspace;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Owns {@code blender/scripts} in the render workspace.
 *
 * <p>Scripts are bundled inside the application under {@code blender/scripts} on the classpath and
 * installed into the workspace on startup. Shipping them with the code means the Python and the Java
 * that calls it are versioned together and can never drift; installing them into the workspace means
 * Blender - which cannot read a jar - always gets a real file, and an operator can look at exactly
 * what ran.
 *
 * <p>Adding a script for a future template is one file in {@code src/main/resources/blender/scripts}.
 */
public final class ClasspathBlenderScriptLibrary implements BlenderScriptLibrary {

    private static final Logger log = LoggerFactory.getLogger(ClasspathBlenderScriptLibrary.class);

    private static final String BUNDLED_SCRIPT_PATTERN = "classpath*:blender/scripts/*.py";

    private final Path scriptsDirectory;
    private final ResourcePatternResolver resourceResolver;

    public ClasspathBlenderScriptLibrary(RenderWorkspace renderWorkspace, ResourcePatternResolver resourceResolver) {
        this.scriptsDirectory = Objects.requireNonNull(renderWorkspace, "renderWorkspace must not be null").scripts();
        this.resourceResolver = Objects.requireNonNull(resourceResolver, "resourceResolver must not be null");
    }

    @Override
    public List<Path> installBundledScripts() {
        try {
            Files.createDirectories(scriptsDirectory);
            List<Path> installed = new ArrayList<>();
            for (Resource resource : resourceResolver.getResources(BUNDLED_SCRIPT_PATTERN)) {
                installed.add(install(resource));
            }
            installed.sort(Comparator.naturalOrder());
            return List.copyOf(installed);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not install bundled Blender scripts into " + scriptsDirectory, e);
        }
    }

    @Override
    public Path locate(String scriptName) {
        Objects.requireNonNull(scriptName, "scriptName must not be null");
        Path candidate = scriptsDirectory.resolve(scriptName).normalize();
        // Rejects names that contain path segments, so a template name can never escape the directory.
        if (!scriptsDirectory.equals(candidate.getParent()) || !Files.isRegularFile(candidate)) {
            throw new ScriptNotFoundException(scriptName, scriptsDirectory);
        }
        return candidate;
    }

    private Path install(Resource resource) throws IOException {
        String fileName = resource.getFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IOException("Bundled Blender script " + resource.getDescription() + " has no file name");
        }
        Path target = scriptsDirectory.resolve(fileName);
        byte[] bundled = readBytes(resource);
        if (Files.isRegularFile(target) && Arrays.equals(bundled, Files.readAllBytes(target))) {
            log.debug("Blender script already up to date: {}", target);
            return target;
        }
        Files.write(target, bundled, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        log.info("Installed Blender script {}", target);
        return target;
    }

    private static byte[] readBytes(Resource resource) throws IOException {
        try (InputStream stream = resource.getInputStream()) {
            return stream.readAllBytes();
        }
    }
}
