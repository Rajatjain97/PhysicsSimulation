package com.physicsfactory.infrastructure.filesystem;

import com.physicsfactory.application.port.ExecutableProbe;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Locates executables on the local machine.
 *
 * <p>Accepts three shapes of configuration, which is what operators actually type:
 * an absolute path, a path relative to the working directory, or a bare program name that is looked
 * up on the system search path. On Windows the usual executable suffixes are tried as well, so
 * {@code blender} finds {@code blender.exe}.
 *
 * <p>All environment access happens in {@link #fromSystemEnvironment(Path)}; the constructor takes
 * plain values so tests can drive the class with a fake search path.
 */
public final class LocalExecutableProbe implements ExecutableProbe {

    private static final Logger log = LoggerFactory.getLogger(LocalExecutableProbe.class);

    private static final List<String> NO_SUFFIX = List.of("");
    private static final List<String> WINDOWS_SUFFIXES = List.of("", ".exe", ".cmd", ".bat", ".com");

    private final Path workingDirectory;
    private final List<Path> searchPath;
    private final List<String> executableSuffixes;

    public LocalExecutableProbe(Path workingDirectory, List<Path> searchPath, List<String> executableSuffixes) {
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory must not be null")
                .toAbsolutePath().normalize();
        this.searchPath = List.copyOf(Objects.requireNonNull(searchPath, "searchPath must not be null"));
        this.executableSuffixes =
                List.copyOf(Objects.requireNonNull(executableSuffixes, "executableSuffixes must not be null"));
    }

    /** Builds a probe from the current process environment. */
    public static LocalExecutableProbe fromSystemEnvironment(Path workingDirectory) {
        String operatingSystem = System.getProperty("os.name", "");
        return new LocalExecutableProbe(workingDirectory,
                parseSearchPath(System.getenv("PATH")),
                suffixesFor(operatingSystem));
    }

    @Override
    public Optional<Path> resolve(String configuredLocation) {
        if (configuredLocation == null || configuredLocation.isBlank()) {
            return Optional.empty();
        }
        String location = configuredLocation.trim();
        Path candidate;
        try {
            candidate = Path.of(location);
        } catch (InvalidPathException e) {
            log.debug("Configured executable location '{}' is not a valid path", location, e);
            return Optional.empty();
        }

        if (candidate.isAbsolute() || candidate.getNameCount() > 1) {
            return firstExecutable(workingDirectory.resolve(candidate));
        }
        return searchPath.stream()
                .map(directory -> directory.resolve(candidate))
                .map(this::firstExecutable)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<Path> firstExecutable(Path base) {
        for (String suffix : executableSuffixes) {
            Path withSuffix = suffix.isEmpty() ? base : base.resolveSibling(base.getFileName() + suffix);
            if (Files.isRegularFile(withSuffix) && Files.isExecutable(withSuffix)) {
                return Optional.of(withSuffix.toAbsolutePath().normalize());
            }
        }
        return Optional.empty();
    }

    private static List<String> suffixesFor(String operatingSystem) {
        return operatingSystem.toLowerCase(Locale.ROOT).contains("windows") ? WINDOWS_SUFFIXES : NO_SUFFIX;
    }

    private static List<Path> parseSearchPath(String pathVariable) {
        if (pathVariable == null || pathVariable.isBlank()) {
            return List.of();
        }
        List<Path> directories = new ArrayList<>();
        for (String entry : pathVariable.split(Pattern.quote(File.pathSeparator))) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                directories.add(Path.of(entry.trim()));
            } catch (InvalidPathException e) {
                log.debug("Ignoring unusable PATH entry '{}'", entry, e);
            }
        }
        return List.copyOf(directories);
    }
}
