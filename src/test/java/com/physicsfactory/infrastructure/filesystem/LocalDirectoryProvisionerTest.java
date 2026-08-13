package com.physicsfactory.infrastructure.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.physicsfactory.domain.exception.WorkspaceProvisioningException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDirectoryProvisionerTest {

    private final LocalDirectoryProvisioner provisioner = new LocalDirectoryProvisioner();

    @TempDir
    Path root;

    @Test
    void createsMissingDirectoriesIncludingParents() {
        Path nested = root.resolve("output").resolve("videos");

        boolean created = provisioner.ensureDirectoryExists(nested);

        assertThat(created).isTrue();
        assertThat(nested).isDirectory();
    }

    @Test
    void doesNothingWhenTheDirectoryAlreadyExists() throws IOException {
        Path existing = Files.createDirectory(root.resolve("assets"));

        assertThat(provisioner.ensureDirectoryExists(existing)).isFalse();
        assertThat(existing).isDirectory();
    }

    @Test
    void failsWithAMeaningfulErrorWhenAFileOccupiesThePath() throws IOException {
        Path occupied = Files.createFile(root.resolve("logs"));

        assertThatThrownBy(() -> provisioner.ensureDirectoryExists(occupied))
                .isInstanceOf(WorkspaceProvisioningException.class)
                .hasMessageContaining("non-directory file")
                .hasMessageContaining(occupied.toString());
    }
}
