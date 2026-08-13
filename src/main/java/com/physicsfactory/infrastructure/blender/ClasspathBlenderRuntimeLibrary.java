package com.physicsfactory.infrastructure.blender;

import com.physicsfactory.application.port.BlenderRuntimeLibrary;
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
 * Installs the Blender-side runtime from the classpath into the render workspace.
 *
 * <p>Three trees are mirrored, preserving their sub-paths: the engine modules, the shared asset
 * library and the template modules. A template is a directory, so
 * {@code blender/templates/default_sphere/template.py} lands as
 * {@code <workspace>/blender/templates/default_sphere/template.py} and the registry inside Blender
 * finds it by scanning - Java never opens a template or an asset, or learns what one contains.
 *
 * <p>Adding a template is therefore a new directory under
 * {@code src/main/resources/blender/templates}, and adding a shared material is a new file under
 * {@code src/main/resources/blender/assets/materials}; no Java changes, no configuration changes.
 */
public final class ClasspathBlenderRuntimeLibrary implements BlenderRuntimeLibrary {

    private static final Logger log = LoggerFactory.getLogger(ClasspathBlenderRuntimeLibrary.class);

    private static final String ENGINE_ROOT = "blender/engine/";
    private static final String ASSET_ROOT = "blender/assets/";
    private static final String TEMPLATE_ROOT = "blender/templates/";

    private final RenderWorkspace renderWorkspace;
    private final ResourcePatternResolver resourceResolver;

    public ClasspathBlenderRuntimeLibrary(RenderWorkspace renderWorkspace, ResourcePatternResolver resourceResolver) {
        this.renderWorkspace = Objects.requireNonNull(renderWorkspace, "renderWorkspace must not be null");
        this.resourceResolver = Objects.requireNonNull(resourceResolver, "resourceResolver must not be null");
    }

    @Override
    public List<Path> installRuntime() {
        List<Path> installed = new ArrayList<>();
        installed.addAll(installTree(ENGINE_ROOT, renderWorkspace.engine()));
        installed.addAll(installTree(ASSET_ROOT, renderWorkspace.assets()));
        installed.addAll(installTree(TEMPLATE_ROOT, renderWorkspace.templates()));
        return List.copyOf(installed);
    }

    private List<Path> installTree(String classpathRoot, Path targetDirectory) {
        try {
            Files.createDirectories(targetDirectory);
            List<Path> installed = new ArrayList<>();
            for (Resource resource : resourceResolver.getResources("classpath*:" + classpathRoot + "**")) {
                if (!resource.isReadable()) {
                    continue;
                }
                String relative = relativePath(resource, classpathRoot);
                if (relative == null) {
                    continue;
                }
                installed.add(install(resource, resolveInside(targetDirectory, relative)));
            }
            installed.sort(Comparator.naturalOrder());
            return installed;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not install " + classpathRoot + " into " + targetDirectory, e);
        }
    }

    /** Works for both {@code file:} and {@code jar:} URLs, which is why it matches on the root marker. */
    private static String relativePath(Resource resource, String classpathRoot) throws IOException {
        String url = resource.getURL().toString();
        int start = url.lastIndexOf(classpathRoot);
        if (start < 0) {
            return null;
        }
        String relative = url.substring(start + classpathRoot.length());
        return (relative.isEmpty() || relative.endsWith("/")) ? null : relative;
    }

    private static Path resolveInside(Path targetDirectory, String relative) throws IOException {
        Path target = targetDirectory.resolve(relative).normalize();
        if (!target.startsWith(targetDirectory)) {
            throw new IOException("Bundled resource '" + relative + "' escapes " + targetDirectory);
        }
        return target;
    }

    private static Path install(Resource resource, Path target) throws IOException {
        byte[] bundled = readBytes(resource);
        if (Files.isRegularFile(target) && Arrays.equals(bundled, Files.readAllBytes(target))) {
            log.debug("Blender runtime file already up to date: {}", target);
            return target;
        }
        Files.createDirectories(target.getParent());
        Files.write(target, bundled, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        log.info("Installed Blender runtime file {}", target);
        return target;
    }

    private static byte[] readBytes(Resource resource) throws IOException {
        try (InputStream stream = resource.getInputStream()) {
            return stream.readAllBytes();
        }
    }
}
