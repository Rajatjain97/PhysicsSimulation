package com.physicsfactory.domain.model;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * The structured result of one Blender invocation.
 *
 * <p>This is the whole of what Java learns from a Blender run: what was executed, what came back on
 * each stream, how it ended and how long it took. Note that a non-zero {@link #exitCode()} is a
 * perfectly valid execution - the caller decides whether that is a failure.
 *
 * @param command        the full command line, including the executable
 * @param exitCode       the process exit code
 * @param standardOutput everything Blender wrote to stdout
 * @param standardError  everything Blender wrote to stderr
 * @param duration       wall clock time from process start to exit
 */
public record BlenderExecution(List<String> command,
                               int exitCode,
                               String standardOutput,
                               String standardError,
                               Duration duration) {

    public BlenderExecution {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(standardOutput, "standardOutput must not be null");
        Objects.requireNonNull(standardError, "standardError must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative but was " + duration);
        }
        command = List.copyOf(command);
    }

    public boolean isSuccessful() {
        return exitCode == 0;
    }

    /** The command line as a single string, for logs and error messages. */
    public String commandLine() {
        return String.join(" ", command);
    }
}
