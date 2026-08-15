package com.physicsfactory.infrastructure.bootstrap;

import com.physicsfactory.application.usecase.RenderBatch;
import com.physicsfactory.domain.model.BatchManifest;
import com.physicsfactory.domain.model.BatchRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Renders a batch when the application is started with {@code --batch}.
 *
 * <p>Owns the two things a batch needs that a single render does not: an identifier that cannot
 * collide with an earlier batch, and a way to stop. Ctrl+C sets a flag the batch checks between
 * videos, so an interrupted run finishes the video it is on, writes its manifest, and leaves
 * everything already rendered intact.
 */
public final class BatchRenderRunner implements ApplicationRunner {

    /** Command line option that triggers a batch: {@code --batch}. */
    public static final String BATCH_OPTION = "batch";

    private static final Logger log = LoggerFactory.getLogger(BatchRenderRunner.class);
    private static final DateTimeFormatter BATCH_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_BATCHES_PER_DAY = 999;

    private final RenderBatch renderBatch;
    private final BatchRequest request;
    private final Path workspaceRoot;
    private final boolean dryRun;

    public BatchRenderRunner(RenderBatch renderBatch, BatchRequest request, Path workspaceRoot, boolean dryRun) {
        this.renderBatch = Objects.requireNonNull(renderBatch, "renderBatch must not be null");
        this.request = Objects.requireNonNull(request, "request must not be null");
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
        this.dryRun = dryRun;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(BATCH_OPTION)) {
            return;
        }
        String batchId = nextBatchId();

        if (dryRun) {
            reportDryRun(batchId);
            return;
        }

        AtomicBoolean cancelled = new AtomicBoolean(false);
        Thread interrupt = new Thread(() -> {
            cancelled.set(true);
            log.warn("Interrupted: no further videos will be started");
        }, "batch-cancel");
        Runtime.getRuntime().addShutdownHook(interrupt);
        try {
            BatchManifest manifest = renderBatch.execute(request, batchId, cancelled);
            if (manifest.failed() > 0) {
                log.warn("{} of {} videos failed; see the batch manifest for the reasons",
                        manifest.failed(), manifest.requested());
            }
        } finally {
            // Removing the hook fails only while the JVM is already shutting down, which is fine.
            try {
                Runtime.getRuntime().removeShutdownHook(interrupt);
            } catch (IllegalStateException ignored) {
                log.debug("Shutdown already in progress");
            }
        }
    }

    private void reportDryRun(String batchId) {
        log.info("Dry run - nothing will be rendered");
        log.info("  batch      : {}", batchId);
        log.info("  template   : {}", request.template());
        log.info("  count      : {}", request.count());
        log.info("  output     : {}", workspaceRoot.resolve(request.outputDirectory()).resolve(batchId));
        log.info("  batch seed : {}", request.seed());
        List<Long> seeds = RenderBatch.deriveSeeds(request.seed(), request.count());
        for (int index = 1; index <= request.count(); index++) {
            log.info("  {} -> seed {}", RenderBatch.videoFileName(request.template(), index), seeds.get(index - 1));
        }
    }

    /**
     * The next free {@code yyyyMMdd-NNN} under the batch directory.
     *
     * <p>Chosen by looking at what is already there, so a second batch on the same day can never
     * overwrite the first.
     */
    private String nextBatchId() {
        Path batches = workspaceRoot.resolve(request.outputDirectory());
        String today = LocalDate.now().format(BATCH_DATE);
        for (int sequence = 1; sequence <= MAX_BATCHES_PER_DAY; sequence++) {
            String candidate = String.format("%s-%03d", today, sequence);
            if (!Files.exists(batches.resolve(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("More than " + MAX_BATCHES_PER_DAY + " batches in " + batches + " today");
    }

}
