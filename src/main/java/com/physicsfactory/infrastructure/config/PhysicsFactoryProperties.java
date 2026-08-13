package com.physicsfactory.infrastructure.config;

import com.physicsfactory.domain.model.WorkspaceDirectory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
 */
@ConfigurationProperties(prefix = "physics-factory")
@Validated
public record PhysicsFactoryProperties(@Valid @NotNull Workspace workspace,
                                       @Valid @NotNull Blender blender) {

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
}
