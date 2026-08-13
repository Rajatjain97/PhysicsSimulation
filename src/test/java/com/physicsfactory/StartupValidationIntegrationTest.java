package com.physicsfactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.physicsfactory.domain.exception.BlenderNotFoundException;
import com.physicsfactory.domain.exception.WorkspaceConfigurationException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.NestedExceptionUtils;

/**
 * Verifies that an unusable environment aborts startup instead of leaving a half configured
 * application running.
 */
class StartupValidationIntegrationTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void abortsStartupWhenTheBlenderExecutableIsMissing() {
        SpringApplicationBuilder application = application(
                "physics-factory.blender.executable-path=" + workspaceRoot.resolve("no-such-blender"));

        Throwable thrown = catchThrowable(application::run);

        assertThat(thrown).isNotNull();
        assertThat(NestedExceptionUtils.getRootCause(thrown)).isInstanceOf(BlenderNotFoundException.class);
        // The workspace is provisioned first, so the failure is captured in the log directory.
        assertThat(workspaceRoot.resolve("logs")).isDirectory();
    }

    @Test
    void abortsStartupWhenAConfiguredDirectoryEscapesTheWorkspaceRoot() {
        SpringApplicationBuilder application = application(
                "physics-factory.workspace.directories.video-output=../videos");

        Throwable thrown = catchThrowable(application::run);

        assertThat(thrown).isNotNull();
        assertThat(NestedExceptionUtils.getRootCause(thrown)).isInstanceOf(WorkspaceConfigurationException.class);
    }

    private SpringApplicationBuilder application(String... overrides) {
        String[] properties = new String[overrides.length + 1];
        properties[0] = "physics-factory.workspace.root=" + workspaceRoot;
        System.arraycopy(overrides, 0, properties, 1, overrides.length);
        return new SpringApplicationBuilder(PhysicsFactoryApplication.class)
                .registerShutdownHook(false)
                .properties(properties);
    }
}
