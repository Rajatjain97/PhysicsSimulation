package com.physicsfactory.infrastructure.blender;

import com.physicsfactory.application.port.BlenderProcessRunner;
import com.physicsfactory.application.usecase.ValidateBlenderInstallation;
import com.physicsfactory.domain.exception.BlenderExecutionException;
import com.physicsfactory.domain.exception.BlenderTimeoutException;
import com.physicsfactory.domain.exception.ScriptNotFoundException;
import com.physicsfactory.domain.model.BlenderExecution;
import com.physicsfactory.domain.model.BlenderInstallation;
import com.physicsfactory.domain.model.BlenderScriptRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs Blender as an external process.
 *
 * <p>This class is the entire Java to Blender boundary. It knows three Blender command line flags and
 * nothing else about the renderer - no cameras, no materials, no scenes - which is what keeps the
 * "Java orchestrates, Blender renders" split honest.
 *
 * <p>Two implementation choices worth remembering:
 *
 * <ul>
 *   <li><b>The executable is re-validated on every invocation.</b> Blender lives outside our control
 *       and a desktop application can run for days, so resolving it once at startup would mean
 *       failing with a confusing error long after somebody moved or upgraded it. The check is a
 *       filesystem stat, and reusing {@link ValidateBlenderInstallation} means there is one
 *       definition of "usable Blender" in the codebase.
 *   <li><b>Both output streams are drained on virtual threads.</b> Blender is chatty; if stdout were
 *       read on the calling thread the pipe could fill and deadlock before the timeout was ever
 *       evaluated.
 *   <li><b>Output is collected line by line, not in one read at the end.</b> A render that has to be
 *       killed is exactly the render whose output is most worth having - it is the one that was too
 *       slow - and reading the whole stream in a single call means a killed process yields nothing.
 *       Reading incrementally keeps everything that arrived before the kill.
 * </ul>
 */
public final class ProcessBlenderRunner implements BlenderProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessBlenderRunner.class);

    /**
     * How long to keep reading a killed process's output. Blender can leave a child holding the pipe
     * open, and waiting forever for a process we already gave up on would defeat the timeout.
     */
    private static final Duration STREAM_DRAIN_GRACE = Duration.ofSeconds(2);

    /**
     * Blender's own progress chatter is enormous and says nothing useful once a render is working.
     * These are the prefixes of the key=value contract the engine prints, and they are the lines
     * worth putting in the application log as they arrive: what the scene did, what the physics did,
     * and how long each stage took.
     */
    private static final List<String> REPORTED_PREFIXES = List.of("render.", "physics.", "timing.");

    /** How much collected output to show when a render had to be killed. */
    private static final int TIMEOUT_REPORT_LINES = 12;

    private static final String VERSION_FLAG = "--version";
    private static final String BACKGROUND_FLAG = "--background";
    private static final String PYTHON_FLAG = "--python";
    private static final String SCRIPT_ARGUMENT_SEPARATOR = "--";

    private final ValidateBlenderInstallation validateBlenderInstallation;
    private final String configuredExecutable;
    private final Path workingDirectory;

    /**
     * @param validateBlenderInstallation resolves and verifies the executable before each invocation
     * @param configuredExecutable        the configured Blender location, exactly as the operator wrote it
     * @param workingDirectory            working directory for Blender; every relative path in a scene
     *                                    contract is resolved against it
     */
    public ProcessBlenderRunner(ValidateBlenderInstallation validateBlenderInstallation,
                                String configuredExecutable,
                                Path workingDirectory) {
        this.validateBlenderInstallation =
                Objects.requireNonNull(validateBlenderInstallation, "validateBlenderInstallation must not be null");
        this.configuredExecutable = Objects.requireNonNull(configuredExecutable, "configuredExecutable must not be null");
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory must not be null");
    }

    @Override
    public BlenderExecution runVersionProbe(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        return execute(List.of(VERSION_FLAG), timeout, "version probe");
    }

    @Override
    public BlenderExecution runScript(BlenderScriptRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!Files.isRegularFile(request.script())) {
            throw new ScriptNotFoundException(request.script().getFileName().toString(), request.script().getParent());
        }
        List<String> arguments = new ArrayList<>(List.of(BACKGROUND_FLAG, PYTHON_FLAG, request.script().toString()));
        if (!request.arguments().isEmpty()) {
            arguments.add(SCRIPT_ARGUMENT_SEPARATOR);
            arguments.addAll(request.arguments());
        }
        return execute(arguments, request.timeout(), "script " + request.script().getFileName());
    }

    private BlenderExecution execute(List<String> blenderArguments, Duration timeout, String description) {
        BlenderInstallation blender = validateBlenderInstallation.execute(configuredExecutable);

        List<String> command = new ArrayList<>();
        command.add(blender.executable().toString());
        command.addAll(blenderArguments);

        log.info("Blender {} starting | executable={} | arguments={} | timeout={} | workingDirectory={}",
                description, blender.executable(), blenderArguments, timeout, workingDirectory);

        Instant start = Instant.now();
        Process process = start(command);
        // Blender's own reporting is logged as it arrives, so a long render says what it is doing
        // instead of going silent for an hour.
        StreamCollector standardOutput = StreamCollector.draining(process.getInputStream(),
                line -> reportProgress(description, line));
        StreamCollector standardError = StreamCollector.draining(process.getErrorStream(), line -> { });
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                // Blender spawns helpers; killing only the parent would leave them running.
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                standardOutput.join(STREAM_DRAIN_GRACE);
                standardError.join(STREAM_DRAIN_GRACE);
                // What it managed to report before being killed is the only evidence of where the
                // time went, so it is logged rather than discarded with the process.
                log.error("Blender {} timed out after {} and was terminated | last reported: {}",
                        description, timeout, standardOutput.reportedTail(TIMEOUT_REPORT_LINES));
                throw new BlenderTimeoutException(command, timeout);
            }
            standardOutput.join();
            standardError.join();

            BlenderExecution execution = new BlenderExecution(command, process.exitValue(),
                    standardOutput.text(), standardError.text(), Duration.between(start, Instant.now()));
            logOutcome(description, execution);
            return execution;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new BlenderExecutionException("Interrupted while waiting for Blender " + description + ".", e);
        }
    }

    /** Logs one line of Blender's output when it is part of the engine's key=value contract. */
    private static void reportProgress(String description, String line) {
        String trimmed = line.strip();
        if (REPORTED_PREFIXES.stream().anyMatch(trimmed::startsWith)) {
            log.info("Blender {} | {}", description, trimmed);
        }
    }

    private Process start(List<String> command) {
        try {
            return new ProcessBuilder(command).directory(workingDirectory.toFile()).start();
        } catch (IOException e) {
            throw new BlenderExecutionException("Could not start Blender: " + String.join(" ", command), e);
        }
    }

    private static void logOutcome(String description, BlenderExecution execution) {
        if (execution.isSuccessful()) {
            log.info("Blender {} succeeded | exitCode={} | duration={}ms",
                    description, execution.exitCode(), execution.duration().toMillis());
        } else {
            log.warn("Blender {} failed | exitCode={} | duration={}ms | stderr={}",
                    description, execution.exitCode(), execution.duration().toMillis(),
                    execution.standardError().isBlank() ? "(empty)" : execution.standardError().strip());
        }
    }

    /**
     * Reads one process stream on a virtual thread, so neither stream can block the timeout from
     * being enforced, and hands every line to an observer as it arrives.
     *
     * <p>Lines are appended as they are read rather than collected in one call at the end. That is
     * what makes the output of a killed render survivable: a single read that never returns leaves
     * nothing behind, and the render we most want to explain is the one that had to be killed.
     */
    private static final class StreamCollector {

        private final StringBuilder collected = new StringBuilder();
        private final List<String> reported = new ArrayList<>();
        private final Thread thread;

        private StreamCollector(Thread thread) {
            this.thread = thread;
        }

        static StreamCollector draining(InputStream stream, Consumer<String> observer) {
            AtomicReference<StreamCollector> self = new AtomicReference<>();
            Thread thread = Thread.ofVirtual().unstarted(() -> {
                StreamCollector collector = self.get();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                        collector.accept(line);
                        observer.accept(line);
                    }
                } catch (IOException e) {
                    // The process was killed while we were reading; whatever arrived is kept.
                    log.debug("Stopped reading a Blender stream early", e);
                }
            });
            StreamCollector collector = new StreamCollector(thread);
            self.set(collector);
            thread.start();
            return collector;
        }

        private synchronized void accept(String line) {
            collected.append(line).append(System.lineSeparator());
            String trimmed = line.strip();
            if (REPORTED_PREFIXES.stream().anyMatch(trimmed::startsWith)) {
                reported.add(trimmed);
            }
        }

        void join() throws InterruptedException {
            thread.join();
        }

        void join(Duration grace) throws InterruptedException {
            thread.join(grace.toMillis());
        }

        synchronized String text() {
            return collected.toString();
        }

        /** The last few contract lines seen, for explaining a render that never finished. */
        synchronized String reportedTail(int limit) {
            if (reported.isEmpty()) {
                return "(nothing reported)";
            }
            return String.join(" | ", reported.subList(Math.max(0, reported.size() - limit), reported.size()));
        }
    }
}
