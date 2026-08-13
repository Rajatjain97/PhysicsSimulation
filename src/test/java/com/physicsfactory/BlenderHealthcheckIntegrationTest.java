package com.physicsfactory;

import static org.assertj.core.api.Assertions.assertThat;

import com.physicsfactory.application.usecase.DetectBlenderVersion;
import com.physicsfactory.application.usecase.RunBlenderHealthcheck;
import com.physicsfactory.domain.model.BlenderVersion;
import com.physicsfactory.domain.model.RenderRequest;
import com.physicsfactory.domain.model.RenderResult;
import com.physicsfactory.domain.model.RenderStatus;
import com.physicsfactory.domain.model.RenderWorkspace;
import com.physicsfactory.infrastructure.filesystem.LocalExecutableProbe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The Story 1.2 integration test: boots the application against a real Blender and runs
 * {@code healthcheck.py} through the whole chain - script installation, scene contract, process
 * execution, structured result.
 *
 * <p>Skipped, not failed, when Blender is not installed. The condition is evaluated before the Spring
 * context is created, so a machine without Blender never even tries to start the application. Set
 * {@code BLENDER_EXECUTABLE} if Blender is not on the {@code PATH}.
 */
@SpringBootTest
@EnabledIf("blenderIsInstalled")
class BlenderHealthcheckIntegrationTest {

    private static final String CONFIGURED_BLENDER =
            Optional.ofNullable(System.getenv("BLENDER_EXECUTABLE")).orElse("blender");

    private static final Path WORKSPACE_ROOT = createTemporaryDirectory();

    @Autowired
    private DetectBlenderVersion detectBlenderVersion;

    @Autowired
    private RunBlenderHealthcheck runBlenderHealthcheck;

    @Autowired
    private RenderWorkspace renderWorkspace;

    @Autowired
    private RenderRequest healthcheckRequest;

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("physics-factory.workspace.root", WORKSPACE_ROOT::toString);
        registry.add("physics-factory.blender.executable-path", () -> CONFIGURED_BLENDER);
    }

    static boolean blenderIsInstalled() {
        return LocalExecutableProbe.fromSystemEnvironment(Path.of("").toAbsolutePath())
                .resolve(CONFIGURED_BLENDER)
                .isPresent();
    }

    @Test
    void detectsTheInstalledBlenderVersion() {
        BlenderVersion version = detectBlenderVersion.execute();

        assertThat(version.major()).isGreaterThanOrEqualTo(3);
        assertThat(version.raw()).startsWith("Blender");
    }

    @Test
    void runsHealthcheckPyInsideBlenderAndReportsWhatItPrinted() throws IOException {
        RenderResult result = runBlenderHealthcheck.execute(healthcheckRequest);

        assertThat(result.status()).isEqualTo(RenderStatus.SUCCEEDED);
        assertThat(result.execution().exitCode()).isZero();
        assertThat(result.execution().duration()).isPositive();
        assertThat(result.execution().standardOutput())
                .contains("blender.version=")
                .contains("python.version=")
                .contains("working.directory=");
        assertThat(result.outputFile()).isEmpty();

        // Blender runs in the workspace root, which is what makes relative paths in a scene contract
        // mean the same thing on both sides. toRealPath() because macOS reports temp dirs through a
        // symlink.
        assertThat(reportedWorkingDirectory(result.execution().standardOutput()))
                .isEqualTo(WORKSPACE_ROOT.toRealPath());
    }

    @Test
    void leavesTheSceneContractInTheRenderCacheForBlenderToConsume() throws IOException {
        runBlenderHealthcheck.execute(healthcheckRequest);

        try (Stream<Path> cached = Files.list(renderWorkspace.cache())) {
            List<Path> contracts = cached.filter(file -> file.getFileName().toString().endsWith(".scene.json")).toList();
            assertThat(contracts).isNotEmpty();
            assertThat(Files.readString(contracts.get(0)))
                    .contains("\"sceneVersion\"")
                    .contains("\"template\"")
                    .contains("healthcheck");
        }
    }

    private static Path reportedWorkingDirectory(String standardOutput) throws IOException {
        String prefix = "working.directory=";
        String line = standardOutput.lines()
                .filter(printed -> printed.startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("healthcheck.py printed no working directory"));
        return Path.of(line.substring(prefix.length()).trim()).toRealPath();
    }

    @AfterAll
    static void deleteTemporaryWorkspace() throws IOException {
        try (Stream<Path> paths = Files.walk(WORKSPACE_ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static Path createTemporaryDirectory() {
        try {
            return Files.createTempDirectory("physics-reel-studio-healthcheck").toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a temporary workspace for the test", e);
        }
    }
}
