package com.physicsfactory.application.usecase;

import com.physicsfactory.application.port.BatchManifestWriter;
import com.physicsfactory.application.port.DirectoryProvisioner;
import com.physicsfactory.domain.model.BatchEntry;
import com.physicsfactory.domain.model.BatchManifest;
import com.physicsfactory.domain.model.BatchRequest;
import com.physicsfactory.domain.model.RenderRequest;
import com.physicsfactory.domain.model.RenderResult;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders one template many times, each video with its own seed.
 *
 * <p>It orchestrates and nothing else: every video goes through the same {@link RenderScene} the
 * single-render path uses, so there is exactly one rendering implementation in the codebase and a
 * batch can never drift from it.
 *
 * <p>Two properties matter more than speed here. Renders run one at a time, because two Blenders
 * competing for the same machine is slower and far less stable than one. And a failure is data, not
 * an abort: video seven failing leaves one to six on disk, records why seven failed, and carries on
 * with eight.
 */
public final class RenderBatch {

    private static final Logger log = LoggerFactory.getLogger(RenderBatch.class);

    private static final String BATCH_MANIFEST = "batch-manifest.json";
    private static final String VIDEO_EXTENSION = ".mp4";
    private static final long MAX_SEED = 1_000_000_000L;

    private final RenderScene renderScene;
    private final DirectoryProvisioner directoryProvisioner;
    private final BatchManifestWriter batchManifestWriter;
    private final Path workspaceRoot;

    public RenderBatch(RenderScene renderScene,
                       DirectoryProvisioner directoryProvisioner,
                       BatchManifestWriter batchManifestWriter,
                       Path workspaceRoot) {
        this.renderScene = Objects.requireNonNull(renderScene, "renderScene must not be null");
        this.directoryProvisioner = Objects.requireNonNull(directoryProvisioner, "directoryProvisioner must not be null");
        this.batchManifestWriter = Objects.requireNonNull(batchManifestWriter, "batchManifestWriter must not be null");
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
    }

    /**
     * Renders the batch and returns its manifest, which is also written to disk.
     *
     * @param cancelled checked before each video, so an interrupted batch stops starting new work and
     *                  still writes a manifest for what it did finish
     */
    public BatchManifest execute(BatchRequest request, String batchId, AtomicBoolean cancelled) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(batchId, "batchId must not be null");
        Objects.requireNonNull(cancelled, "cancelled must not be null");

        Path batchDirectory = request.outputDirectory().resolve(batchId);
        directoryProvisioner.ensureDirectoryExists(workspaceRoot.resolve(batchDirectory));

        List<Long> seeds = deriveSeeds(request.seed(), request.count());
        log.info("Batch {} | template {} | {} videos | batch seed {} | quality {}",
                batchId, request.template(), request.count(), request.seed(), request.quality());

        List<BatchEntry> entries = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;

        for (int index = 1; index <= request.count(); index++) {
            if (cancelled.get()) {
                log.warn("Batch {} interrupted: stopping after {} of {} videos",
                        batchId, index - 1, request.count());
                break;
            }
            BatchEntry entry = renderOne(request, batchDirectory, index, seeds.get(index - 1));
            entries.add(entry);
            if (entry.isSuccessful()) {
                succeeded++;
            } else {
                failed++;
            }
            log.info("Batch {} | progress {}/{} | success {} | failed {}",
                    batchId, index, request.count(), succeeded, failed);
        }

        BatchManifest manifest = new BatchManifest(batchId, request.template(), request.count(), succeeded,
                failed, request.seed(), Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                request.quality(), request.parameters(), entries);
        Path manifestFile = workspaceRoot.resolve(batchDirectory).resolve(BATCH_MANIFEST);
        batchManifestWriter.write(manifest, manifestFile);

        log.info("Batch {} finished | SUCCESS: {} | FAILED: {} | manifest {}",
                batchId, succeeded, failed, manifestFile);
        return manifest;
    }

    /**
     * The seeds a batch will use.
     *
     * <p>Derived from the batch seed through {@link Random}, whose sequence is specified rather than
     * implementation-defined, so the same batch seed gives the same videos on any machine.
     */
    public static List<Long> deriveSeeds(long batchSeed, int count) {
        Random sequence = new Random(batchSeed);
        List<Long> seeds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            seeds.add(Math.floorMod(sequence.nextLong(), MAX_SEED));
        }
        return List.copyOf(seeds);
    }

    /** {@code marble_arena_003.mp4} - sorts correctly and says what it is. */
    public static String videoFileName(String template, int index) {
        return String.format("%s_%03d%s", toFileStem(template), index, VIDEO_EXTENSION);
    }

    private BatchEntry renderOne(BatchRequest request, Path batchDirectory, int index, long seed) {
        Path video = batchDirectory.resolve(videoFileName(request.template(), index));
        Map<String, Object> parameters = new LinkedHashMap<>(request.parameters());
        parameters.put(RenderScene.SEED_PARAMETER, seed);

        Instant started = Instant.now();
        try {
            RenderResult result = renderScene.execute(
                    new RenderRequest(request.template(), video, request.timeout(), request.quality()),
                    parameters);
            long elapsed = Duration.between(started, Instant.now()).toMillis();

            if (result.isSuccessful()) {
                return BatchEntry.succeeded(index, seed, video.toString(), manifestFor(video).toString(), elapsed);
            }
            // Blender ran and said no: keep its own words rather than inventing a message.
            String stderr = result.execution().standardError().strip();
            return BatchEntry.failed(index, seed,
                    "exit code " + result.execution().exitCode() + (stderr.isEmpty() ? "" : ": " + stderr), elapsed);
        } catch (RuntimeException e) {
            // One video failing is not the batch failing. Record why, and carry on.
            long elapsed = Duration.between(started, Instant.now()).toMillis();
            log.error("Batch video {} failed: {}", index, e.getMessage());
            return BatchEntry.failed(index, seed, e.getClass().getSimpleName() + ": " + e.getMessage(), elapsed);
        }
    }

    private static Path manifestFor(Path video) {
        String name = video.getFileName().toString();
        return video.resolveSibling(name.substring(0, name.lastIndexOf('.')) + ".json");
    }

    private static String toFileStem(String template) {
        return template.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
