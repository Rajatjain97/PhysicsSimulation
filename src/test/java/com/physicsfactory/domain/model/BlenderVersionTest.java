package com.physicsfactory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BlenderVersionTest {

    @Test
    void parsesTheVersionBlenderPrintsOnStartup() {
        String output = """
                Blender 4.2.1 LTS
                \tbuild date: 2024-09-24
                \tbuild platform: Linux
                """;

        BlenderVersion version = BlenderVersion.parse(output).orElseThrow();

        assertThat(version.major()).isEqualTo(4);
        assertThat(version.minor()).isEqualTo(2);
        assertThat(version.patch()).isEqualTo(1);
        assertThat(version.shortVersion()).isEqualTo("4.2.1");
        assertThat(version.raw()).isEqualTo("Blender 4.2.1");
    }

    @Test
    void treatsAMissingPatchComponentAsZero() {
        BlenderVersion version = BlenderVersion.parse("Blender 3.6").orElseThrow();

        assertThat(version.shortVersion()).isEqualTo("3.6.0");
    }

    @Test
    void returnsEmptyWhenTheOutputContainsNoVersion() {
        assertThat(BlenderVersion.parse(null)).isEmpty();
        assertThat(BlenderVersion.parse("")).isEmpty();
        assertThat(BlenderVersion.parse("   ")).isEmpty();
        assertThat(BlenderVersion.parse("command not found: blender")).isEmpty();
        assertThat(BlenderVersion.parse("Blender")).isEmpty();
    }
}
