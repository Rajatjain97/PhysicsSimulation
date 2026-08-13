package com.physicsfactory;

import static org.assertj.core.api.Assertions.assertThat;

import com.physicsfactory.application.usecase.BootstrapEnvironment;
import com.physicsfactory.domain.model.WorkspaceDirectory;
import com.physicsfactory.domain.model.WorkspaceLayout;
import com.physicsfactory.infrastructure.config.PhysicsFactoryProperties;
import com.physicsfactory.support.FakeExecutable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * End to end check of Story 1.1: booting the application against a temporary workspace and a fake
 * Blender binary must provision every directory and wire the bootstrap use case.
 *
 * <p>Spring Boot invokes {@code ApplicationRunner} beans when the test context is created, so
 * asserting on the filesystem afterwards exercises the real startup path.
 */
@SpringBootTest
class PhysicsFactoryApplicationTests {

    private static final Path WORKSPACE_ROOT = createTemporaryDirectory("physics-factory-workspace");
    private static final Path TOOL_DIRECTORY = createTemporaryDirectory("physics-factory-tools");
    private static final Path FAKE_BLENDER = createFakeBlender();

    @Autowired
    private WorkspaceLayout workspaceLayout;

    @Autowired
    private PhysicsFactoryProperties properties;

    @Autowired
    private BootstrapEnvironment bootstrapEnvironment;

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("physics-factory.workspace.root", WORKSPACE_ROOT::toString);
        registry.add("physics-factory.blender.executable-path", FAKE_BLENDER::toString);
    }

    @Test
    void createsEveryConfiguredDirectoryDuringStartup() {
        assertThat(workspaceLayout.allDirectories()).isNotEmpty();
        assertThat(workspaceLayout.allDirectories()).allSatisfy(directory -> assertThat(directory).isDirectory());
    }

    @Test
    void bindsTheWorkspaceLayoutDeclaredInApplicationYaml() {
        assertThat(workspaceLayout.root()).isEqualTo(WORKSPACE_ROOT.toAbsolutePath().normalize());
        assertThat(workspaceLayout.pathOf(WorkspaceDirectory.VIDEO_OUTPUT))
                .isEqualTo(WORKSPACE_ROOT.resolve("output").resolve("videos"));
        assertThat(workspaceLayout.pathOf(WorkspaceDirectory.THUMBNAIL_OUTPUT))
                .isEqualTo(WORKSPACE_ROOT.resolve("output").resolve("thumbnails"));
        assertThat(properties.workspace().directories())
                .containsEntry(WorkspaceDirectory.ASSETS, "assets")
                .containsEntry(WorkspaceDirectory.CONFIGS, "configs")
                .containsEntry(WorkspaceDirectory.LOGS, "logs");
    }

    @Test
    void wiresTheBootstrapUseCase() {
        assertThat(bootstrapEnvironment).isNotNull();
    }

    @AfterAll
    static void deleteTemporaryDirectories() throws IOException {
        for (Path directory : List.of(WORKSPACE_ROOT, TOOL_DIRECTORY)) {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static Path createTemporaryDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix).toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a temporary directory for the test", e);
        }
    }

    private static Path createFakeBlender() {
        try {
            return FakeExecutable.create(TOOL_DIRECTORY, "blender");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the fake Blender executable", e);
        }
    }
}
