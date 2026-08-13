package com.physicsfactory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.physicsfactory.domain.exception.WorkspaceConfigurationException;
import com.physicsfactory.support.WorkspaceLayouts;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceLayoutTest {

    @TempDir
    Path root;

    @Test
    void resolvesEveryDirectoryAgainstTheRoot() {
        WorkspaceLayout layout = WorkspaceLayouts.rootedAt(root);

        assertThat(layout.root()).isEqualTo(root.toAbsolutePath().normalize());
        assertThat(layout.pathOf(WorkspaceDirectory.ASSETS)).isEqualTo(root.resolve("assets"));
        assertThat(layout.pathOf(WorkspaceDirectory.CONFIGS)).isEqualTo(root.resolve("configs"));
        assertThat(layout.pathOf(WorkspaceDirectory.VIDEO_OUTPUT)).isEqualTo(root.resolve("output").resolve("videos"));
        assertThat(layout.pathOf(WorkspaceDirectory.THUMBNAIL_OUTPUT))
                .isEqualTo(root.resolve("output").resolve("thumbnails"));
        assertThat(layout.pathOf(WorkspaceDirectory.LOGS)).isEqualTo(root.resolve("logs"));
    }

    @Test
    void makesRelativeRootsAbsolute() {
        WorkspaceLayout layout = WorkspaceLayouts.rootedAt(Path.of("build", "test-workspace"));

        assertThat(layout.root()).isAbsolute();
        assertThat(layout.root()).endsWith(Path.of("build", "test-workspace"));
    }

    @Test
    void exposesDirectoriesInDeclarationOrder() {
        WorkspaceLayout layout = WorkspaceLayouts.rootedAt(root);

        assertThat(layout.directories().keySet()).containsExactly(
                WorkspaceDirectory.ASSETS,
                WorkspaceDirectory.CONFIGS,
                WorkspaceDirectory.VIDEO_OUTPUT,
                WorkspaceDirectory.THUMBNAIL_OUTPUT,
                WorkspaceDirectory.LOGS);
        assertThat(layout.allDirectories()).hasSize(WorkspaceDirectory.values().length);
    }

    @Test
    void isImmutable() {
        Map<WorkspaceDirectory, String> mutableSource = WorkspaceLayouts.defaultRelativePaths();
        WorkspaceLayout layout = WorkspaceLayout.of(root, mutableSource);

        mutableSource.remove(WorkspaceDirectory.LOGS);

        assertThat(layout.pathOf(WorkspaceDirectory.LOGS)).isEqualTo(root.resolve("logs"));
        assertThatThrownBy(() -> layout.directories().put(WorkspaceDirectory.LOGS, root))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsAnIncompleteConfiguration() {
        Map<WorkspaceDirectory, String> incomplete = WorkspaceLayouts.defaultRelativePaths();
        incomplete.remove(WorkspaceDirectory.THUMBNAIL_OUTPUT);

        assertThatThrownBy(() -> WorkspaceLayout.of(root, incomplete))
                .isInstanceOf(WorkspaceConfigurationException.class)
                .hasMessageContaining("thumbnail-output");
    }

    @Test
    void rejectsABlankPath() {
        Map<WorkspaceDirectory, String> blank = WorkspaceLayouts.defaultRelativePaths();
        blank.put(WorkspaceDirectory.ASSETS, "   ");

        assertThatThrownBy(() -> WorkspaceLayout.of(root, blank))
                .isInstanceOf(WorkspaceConfigurationException.class)
                .hasMessageContaining("assets");
    }

    @Test
    void rejectsAnAbsolutePath() {
        Map<WorkspaceDirectory, String> absolute = WorkspaceLayouts.defaultRelativePaths();
        absolute.put(WorkspaceDirectory.CONFIGS, root.resolve("elsewhere").toAbsolutePath().toString());

        assertThatThrownBy(() -> WorkspaceLayout.of(root, absolute))
                .isInstanceOf(WorkspaceConfigurationException.class)
                .hasMessageContaining("must be relative");
    }

    @Test
    void rejectsAPathThatEscapesTheRoot() {
        Map<WorkspaceDirectory, String> escaping = WorkspaceLayouts.defaultRelativePaths();
        escaping.put(WorkspaceDirectory.VIDEO_OUTPUT, "../videos");

        assertThatThrownBy(() -> WorkspaceLayout.of(root, escaping))
                .isInstanceOf(WorkspaceConfigurationException.class)
                .hasMessageContaining("escapes the workspace root");
    }

    @Test
    void rejectsAnIncompleteMapPassedToTheConstructor() {
        Map<WorkspaceDirectory, Path> onlyOne = new EnumMap<>(WorkspaceDirectory.class);
        onlyOne.put(WorkspaceDirectory.ASSETS, root.resolve("assets"));

        assertThatThrownBy(() -> new WorkspaceLayout(root, onlyOne))
                .isInstanceOf(WorkspaceConfigurationException.class);
    }
}
