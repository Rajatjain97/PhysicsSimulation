package com.physicsfactory.infrastructure.config;

import com.physicsfactory.domain.model.WorkspaceDirectory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, immutable view of the {@code physics-factory} section of {@code application.yaml}.
 *
 * <p>Records give constructor binding and immutability for free. Validation annotations mean a
 * malformed configuration fails during context startup with a precise message, before any use case
 * runs.
 *
 * @param workspace where Physics Factory keeps its files
 * @param blender   how to reach Blender and how long it may take
 * @param render    the scene rendered by {@code --render}
 * @param batch     the batch rendered by {@code --batch}
 */
@ConfigurationProperties(prefix = "physics-factory")
@Validated
public record PhysicsFactoryProperties(@Valid @NotNull Workspace workspace,
                                       @Valid @NotNull Blender blender,
                                       @Valid @NotNull Render render,
                                       @Valid @NotNull Batch batch) {

    /**
     * @param root        workspace root; may be absolute or relative to the process working directory
     * @param directories one path per {@link WorkspaceDirectory}, relative to {@code root}
     */
    public record Workspace(@NotBlank String root,
                            @NotEmpty Map<WorkspaceDirectory, @NotBlank String> directories) {
    }

    /**
     * @param executablePath absolute path, relative path, or bare program name of the Blender binary
     * @param versionTimeout how long {@code blender --version} may take
     * @param healthcheck    the job run by {@code --healthcheck}
     */
    public record Blender(@NotBlank String executablePath,
                          @NotNull Duration versionTimeout,
                          @Valid @NotNull Healthcheck healthcheck) {
    }

    /**
     * @param template   template name, which also names the Blender script that runs it
     * @param outputFile where the template would write its video, relative to the workspace root
     * @param timeout    how long the healthcheck may take
     */
    public record Healthcheck(@NotBlank String template,
                              @NotBlank String outputFile,
                              @NotNull Duration timeout) {
    }

    /**
     * @param template   name of the Blender template to render
     * @param outputFile where the render belongs, relative to the workspace root; the extension
     *                   decides whether Java expects a still or a movie
     * @param timeout    how long the render may take - a sixty second reel is hundreds of frames
     * @param parameters template-specific input, carried into the scene contract untouched
     */
    public record Render(@NotBlank String template,
                         @NotBlank String outputFile,
                         @NotNull Duration timeout,
                         @NotNull Map<String, Object> parameters) {
    }

    /**
     * @param template   template every video in the batch uses
     * @param count      how many videos to render
     * @param seed       batch seed; leave it out and one is generated and recorded
     * @param dryRun     print the plan and the seeds without launching Blender
     * @param timeout    render budget for a single video
     * @param parameters parameters shared by every video; each video's seed is added per render
     */
    public record Batch(@NotBlank String template,
                        @Positive int count,
                        Long seed,
                        boolean dryRun,
                        @NotNull Duration timeout,
                        @NotNull Map<String, Object> parameters) {
    }
}
