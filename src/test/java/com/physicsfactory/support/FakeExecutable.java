package com.physicsfactory.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;

/** Creates an empty file that the operating system considers executable. */
public final class FakeExecutable {

    private FakeExecutable() {
    }

    /**
     * @param directory an existing directory
     * @param name      program name without extension; {@code .exe} is appended on Windows
     * @return the absolute path of the created file
     */
    public static Path create(Path directory, String name) throws IOException {
        Path executable = Files.createFile(directory.resolve(isWindows() ? name + ".exe" : name));
        try {
            Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException e) {
            // Windows: no POSIX permissions, fall back to the java.io flag.
            if (!executable.toFile().setExecutable(true)) {
                throw new IOException("Could not mark " + executable + " as executable");
            }
        }
        return executable.toAbsolutePath().normalize();
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }
}
