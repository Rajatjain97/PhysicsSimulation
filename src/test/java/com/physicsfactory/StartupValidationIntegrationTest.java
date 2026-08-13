package com.physicsfactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.physicsfactory.domain.exception.BlenderNotFoundException;
import com.physicsfactory.domain.exception.WorkspaceConfigurationException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Verifies that an unusable environment aborts startup instead of leaving a half configured
 * application running.
 *
 * <p>Overrides are passed as command line arguments rather than through
 * {@code SpringApplicationBuilder.properties(...)}: those become <em>default</em> properties, which
 * sit below {@code application.yaml} and below the {@code BLENDER_EXECUTABLE} environment variable,
 * so the test would silently exercise the developer's own machine instead of the case it names.
 */
class StartupValidationIntegrationTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void abortsStartupWhenTheBlenderExecutableIsMissing() {
        Throwable thrown = catchThrowable(() ->
                run("--physics-factory.blender.executable-path=" + workspaceRoot.resolve("no-such-blender")));

        assertThat(thrown).isNotNull();
        assertThat(causeChainOf(thrown)).hasAtLeastOneElementOfType(BlenderNotFoundException.class);
        // The workspace is provisioned first, so the failure is captured in the log directory.
        assertThat(workspaceRoot.resolve("logs")).isDirectory();
    }

    @Test
    void abortsStartupWhenAConfiguredDirectoryEscapesTheWorkspaceRoot() {
        Throwable thrown = catchThrowable(() ->
                run("--physics-factory.workspace.directories.video-output=../videos"));

        assertThat(thrown).isNotNull();
        assertThat(causeChainOf(thrown)).hasAtLeastOneElementOfType(WorkspaceConfigurationException.class);
    }

    private void run(String... overrides) {
        String[] arguments = new String[overrides.length + 1];
        arguments[0] = "--physics-factory.workspace.root=" + workspaceRoot;
        System.arraycopy(overrides, 0, arguments, 1, overrides.length);
        new SpringApplicationBuilder(PhysicsFactoryApplication.class)
                .registerShutdownHook(false)
                .run(arguments);
    }

    /**
     * The whole chain, starting with the throwable itself: Spring Boot sometimes rethrows a startup
     * failure unwrapped and sometimes wraps it, and this assertion should not depend on which.
     */
    private static List<Throwable> causeChainOf(Throwable thrown) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable current = thrown; current != null && !chain.contains(current); current = current.getCause()) {
            chain.add(current);
        }
        return chain;
    }
}
