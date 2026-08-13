package com.physicsfactory.infrastructure.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import com.physicsfactory.support.FakeExecutable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalExecutableProbeTest {

    private static final List<String> SUFFIXES = FakeExecutable.isWindows() ? List.of("", ".exe") : List.of("");

    @TempDir
    Path workingDirectory;

    @TempDir
    Path binDirectory;

    @Test
    void resolvesAnAbsolutePath() throws IOException {
        Path blender = FakeExecutable.create(binDirectory, "blender");

        assertThat(probe().resolve(blender.toString())).contains(blender);
    }

    @Test
    void resolvesAPathRelativeToTheWorkingDirectory() throws IOException {
        Path toolDirectory = Files.createDirectory(workingDirectory.resolve("tools"));
        Path blender = FakeExecutable.create(toolDirectory, "blender");

        assertThat(probe().resolve("tools/" + blender.getFileName())).contains(blender);
    }

    @Test
    void resolvesABareProgramNameOnTheSearchPath() throws IOException {
        Path blender = FakeExecutable.create(binDirectory, "blender");

        assertThat(probe().resolve("blender")).contains(blender);
    }

    @Test
    void returnsEmptyWhenTheProgramIsMissing() {
        assertThat(probe().resolve("blender")).isEmpty();
        assertThat(probe().resolve(binDirectory.resolve("blender").toString())).isEmpty();
    }

    @Test
    void returnsEmptyForBlankConfiguration() {
        assertThat(probe().resolve(null)).isEmpty();
        assertThat(probe().resolve("")).isEmpty();
        assertThat(probe().resolve("   ")).isEmpty();
    }

    @Test
    void returnsEmptyWhenThePathIsADirectory() throws IOException {
        Path directory = Files.createDirectory(binDirectory.resolve("blender-dir"));

        assertThat(probe().resolve(directory.toString())).isEmpty();
    }

    @Test
    void ignoresTrailingWhitespaceInConfiguration() throws IOException {
        Path blender = FakeExecutable.create(binDirectory, "blender");

        assertThat(probe().resolve("  " + blender + "  ")).contains(blender);
    }

    private LocalExecutableProbe probe() {
        return new LocalExecutableProbe(workingDirectory, List.of(binDirectory), SUFFIXES);
    }
}
