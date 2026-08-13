package com.physicsfactory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.physicsfactory.support.WorkspaceLayouts;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenderWorkspaceTest {

    @TempDir
    Path root;

    @Test
    void viewsTheBlenderPartOfTheWorkspace() {
        RenderWorkspace workspace = RenderWorkspace.of(WorkspaceLayouts.rootedAt(root));

        assertThat(workspace.scripts()).isEqualTo(root.resolve("blender").resolve("scripts"));
        assertThat(workspace.templates()).isEqualTo(root.resolve("blender").resolve("templates"));
        assertThat(workspace.renders()).isEqualTo(root.resolve("blender").resolve("renders"));
        assertThat(workspace.cache()).isEqualTo(root.resolve("blender").resolve("cache"));
    }

    @Test
    void staysOutOfTheApplicationOutputFolders() {
        WorkspaceLayout layout = WorkspaceLayouts.rootedAt(root);
        RenderWorkspace workspace = RenderWorkspace.of(layout);

        assertThat(workspace.directories()).doesNotContain(
                layout.pathOf(WorkspaceDirectory.VIDEO_OUTPUT),
                layout.pathOf(WorkspaceDirectory.THUMBNAIL_OUTPUT));
        assertThat(workspace.directories()).allSatisfy(directory ->
                assertThat(directory).startsWith(root.resolve("blender")));
    }
}
