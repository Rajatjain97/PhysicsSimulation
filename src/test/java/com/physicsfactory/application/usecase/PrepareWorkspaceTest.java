package com.physicsfactory.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.physicsfactory.domain.model.WorkspaceLayout;
import com.physicsfactory.domain.model.WorkspacePreparation;
import com.physicsfactory.support.RecordingDirectoryProvisioner;
import com.physicsfactory.support.WorkspaceLayouts;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrepareWorkspaceTest {

    @TempDir
    Path root;

    @Test
    void provisionsTheRootFollowedByEveryManagedDirectory() {
        WorkspaceLayout layout = WorkspaceLayouts.rootedAt(root);
        RecordingDirectoryProvisioner provisioner = new RecordingDirectoryProvisioner();

        WorkspacePreparation preparation = new PrepareWorkspace(provisioner).execute(layout);

        List<Path> expected = new ArrayList<>();
        expected.add(layout.root());
        expected.addAll(layout.allDirectories());
        assertThat(provisioner.requests()).containsExactlyElementsOf(expected);
        assertThat(preparation.createdDirectories()).containsExactlyElementsOf(expected);
        assertThat(preparation.wasAlreadyProvisioned()).isFalse();
    }

    @Test
    void reportsOnlyTheDirectoriesThatWereActuallyCreated() {
        WorkspaceLayout layout = WorkspaceLayouts.rootedAt(root);
        RecordingDirectoryProvisioner provisioner =
                new RecordingDirectoryProvisioner(layout.root(), layout.allDirectories().get(0));

        WorkspacePreparation preparation = new PrepareWorkspace(provisioner).execute(layout);

        assertThat(preparation.createdDirectories())
                .doesNotContain(layout.root(), layout.allDirectories().get(0))
                .hasSize(layout.allDirectories().size() - 1);
    }

    @Test
    void isIdempotent() {
        WorkspaceLayout layout = WorkspaceLayouts.rootedAt(root);
        PrepareWorkspace prepareWorkspace = new PrepareWorkspace(new RecordingDirectoryProvisioner());

        prepareWorkspace.execute(layout);
        WorkspacePreparation secondRun = prepareWorkspace.execute(layout);

        assertThat(secondRun.wasAlreadyProvisioned()).isTrue();
        assertThat(secondRun.layout()).isEqualTo(layout);
    }
}
