package com.physicsfactory.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The version of the Blender installation Java is talking to.
 *
 * <p>Parsed from {@code blender --version} output, whose first line looks like
 * {@code Blender 4.2.1 LTS}. Later stories can use this to refuse templates that need a newer
 * Blender than the machine has.
 *
 * @param major major version
 * @param minor minor version
 * @param patch patch version; {@code 0} when Blender only reported two components
 * @param raw   the first line of output, kept verbatim for logs and support requests
 */
public record BlenderVersion(int major, int minor, int patch, String raw) {

    private static final Pattern VERSION_LINE = Pattern.compile("Blender\\s+(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    public BlenderVersion {
        Objects.requireNonNull(raw, "raw must not be null");
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version components must not be negative: " + major + "." + minor + "." + patch);
        }
    }

    /**
     * Extracts the version from {@code blender --version} output.
     *
     * @return the parsed version, or {@link Optional#empty()} if the output does not contain one
     */
    public static Optional<BlenderVersion> parse(String versionOutput) {
        if (versionOutput == null || versionOutput.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = VERSION_LINE.matcher(versionOutput);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = (matcher.group(3) != null) ? Integer.parseInt(matcher.group(3)) : 0;
        return Optional.of(new BlenderVersion(major, minor, patch, matcher.group().trim()));
    }

    /** The version as {@code major.minor.patch}. */
    public String shortVersion() {
        return major + "." + minor + "." + patch;
    }
}
