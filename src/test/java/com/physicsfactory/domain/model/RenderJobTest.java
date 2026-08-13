package com.physicsfactory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RenderJobTest {

    private static final RenderRequest REQUEST =
            new RenderRequest("healthcheck", Path.of("output/videos/demo.mp4"), Duration.ofMinutes(2));

    @Test
    void acceptsARequestByGivingItAnIdentityAndASceneContract() {
        Instant before = Instant.now();

        RenderJob job = RenderJob.create(REQUEST);

        assertThat(job.id()).isNotNull();
        assertThat(job.request()).isEqualTo(REQUEST);
        assertThat(job.scene()).isEqualTo(SceneContract.forRequest(REQUEST));
        assertThat(job.submittedAt()).isBetween(before, Instant.now());
    }

    @Test
    void givesEveryJobItsOwnIdentityAndSceneFileName() {
        RenderJob first = RenderJob.create(REQUEST);
        RenderJob second = RenderJob.create(REQUEST);

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(first.sceneFileName()).isEqualTo(first.id() + ".scene.json");
        assertThat(first.sceneFileName()).isNotEqualTo(second.sceneFileName());
    }
}
