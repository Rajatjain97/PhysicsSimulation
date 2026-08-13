package com.physicsfactory.infrastructure.blender;

import com.physicsfactory.application.port.BlenderProcessRunner;
import com.physicsfactory.application.usecase.ValidateBlenderInstallation;
import com.physicsfactory.domain.exception.BlenderExecutionException;
import com.physicsfactory.domain.exception.BlenderTimeoutException;
import com.physicsfactory.domain.exception.ScriptNotFoundException;
import com.physicsfactory.domain.model.BlenderExecution;
import com.physicsfactory.domain.model.BlenderInstallation;
import com.physicsfactory.domain.model.BlenderScriptRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
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
 * </ul>
 */
public final class ProcessBlenderRunner implements BlenderProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessBlenderRunner.class);

    /**
     * How long to keep reading a killed process's output. Blender can leave a child holding the pipe
     * open, and waiting forever for a process we already gave up on would defeat the timeout.
     */
    private static final Duration STREAM_DRAIN_GRACE = Duration.ofSeconds(2);

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
        StreamCollector standardOutput = StreamCollector.draining(process.getInputStream());
        StreamCollector standardError = StreamCollector.draining(process.getErrorStream());
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                // Blender spawns helpers; killing only the parent would leave them running.
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                standardOutput.join(STREAM_DRAIN_GRACE);
                standardError.join(STREAM_DRAIN_GRACE);
                log.error("Blender {} timed out after {} and was terminated", description, timeout);
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
     * Reads one process stream to exhaustion on a virtual thread, so neither stream can block the
     * timeout from being enforced.
     */
    private static final class StreamCollector {

        private final AtomicReference<String> text;
        private final Thread thread;

        private StreamCollector(Thread thread, AtomicReference<String> text) {
            this.thread = thread;
            this.text = text;
        }

        static StreamCollector draining(InputStream stream) {
            AtomicReference<String> text = new AtomicReference<>("");
            Thread thread = Thread.ofVirtual().start(() -> {
                try (InputStream source = stream) {
                    text.set(new String(source.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    // The process was killed while we were reading; whatever arrived is good enough.
                    log.debug("Stopped reading a Blender stream early", e);
                }
            });
            return new StreamCollector(thread, text);
        }

        void join() throws InterruptedException {
            thread.join();
        }

        void join(Duration grace) throws InterruptedException {
            thread.join(grace.toMillis());
        }

        String text() {
            return text.get();
        }
    }
}
