package com.physicsfactory.infrastructure.config;

import com.physicsfactory.application.port.DirectoryProvisioner;
import com.physicsfactory.application.port.ExecutableProbe;
import com.physicsfactory.application.port.StartupReporter;
import com.physicsfactory.application.usecase.BootstrapEnvironment;
import com.physicsfactory.application.usecase.PrepareWorkspace;
import com.physicsfactory.application.usecase.ValidateBlenderInstallation;
import com.physicsfactory.domain.model.WorkspaceLayout;
import com.physicsfactory.infrastructure.bootstrap.EnvironmentBootstrapRunner;
import com.physicsfactory.infrastructure.diagnostics.StartupExitCodeMapper;
import com.physicsfactory.infrastructure.filesystem.LocalDirectoryProvisioner;
import com.physicsfactory.infrastructure.filesystem.LocalExecutableProbe;
import com.physicsfactory.infrastructure.logging.LoggingStartupReporter;
import java.nio.file.Path;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ExitCodeExceptionMapper;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single wiring point of the application.
 *
 * <p>Adapters and use cases are plain classes without Spring annotations, and they are assembled here
 * with explicit constructor injection. The trade-off is deliberate: component scanning would save a
 * few lines, but one readable graph makes it obvious - two years from now - what depends on what.
 */
@Configuration(proxyBeanMethods = false)
class BootstrapConfiguration {

    /**
     * Resolves and validates the configured workspace layout. Declaring it as a bean means a broken
     * layout fails during context startup, and later stories can inject the layout directly.
     */
    @Bean
    WorkspaceLayout workspaceLayout(PhysicsFactoryProperties properties) {
        return WorkspaceLayout.of(Path.of(properties.workspace().root()), properties.workspace().directories());
    }

    @Bean
    DirectoryProvisioner directoryProvisioner() {
        return new LocalDirectoryProvisioner();
    }

    @Bean
    ExecutableProbe executableProbe(WorkspaceLayout workspaceLayout) {
        // Relative executable paths are resolved against the workspace root rather than whatever
        // directory the process happens to be started from.
        return LocalExecutableProbe.fromSystemEnvironment(workspaceLayout.root());
    }

    @Bean
    StartupReporter startupReporter(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        String name = (build != null) ? build.getName() : "physics-factory";
        String version = (build != null) ? "v" + build.getVersion() : "(version unavailable)";
        return new LoggingStartupReporter(name, version);
    }

    @Bean
    PrepareWorkspace prepareWorkspace(DirectoryProvisioner directoryProvisioner) {
        return new PrepareWorkspace(directoryProvisioner);
    }

    @Bean
    ValidateBlenderInstallation validateBlenderInstallation(ExecutableProbe executableProbe) {
        return new ValidateBlenderInstallation(executableProbe);
    }

    @Bean
    BootstrapEnvironment bootstrapEnvironment(PrepareWorkspace prepareWorkspace,
                                              ValidateBlenderInstallation validateBlenderInstallation,
                                              StartupReporter startupReporter) {
        return new BootstrapEnvironment(prepareWorkspace, validateBlenderInstallation, startupReporter);
    }

    @Bean
    EnvironmentBootstrapRunner environmentBootstrapRunner(BootstrapEnvironment bootstrapEnvironment,
                                                          WorkspaceLayout workspaceLayout,
                                                          PhysicsFactoryProperties properties) {
        return new EnvironmentBootstrapRunner(bootstrapEnvironment, workspaceLayout,
                properties.blender().executablePath());
    }

    @Bean
    ExitCodeExceptionMapper startupExitCodeMapper() {
        return new StartupExitCodeMapper();
    }
}
