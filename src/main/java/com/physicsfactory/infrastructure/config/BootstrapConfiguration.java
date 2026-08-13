package com.physicsfactory.infrastructure.config;

import com.physicsfactory.application.port.BlenderProcessRunner;
import com.physicsfactory.application.port.BlenderScriptLibrary;
import com.physicsfactory.application.port.DirectoryProvisioner;
import com.physicsfactory.application.port.ExecutableProbe;
import com.physicsfactory.application.port.SceneContractWriter;
import com.physicsfactory.application.port.StartupReporter;
import com.physicsfactory.application.usecase.BootstrapEnvironment;
import com.physicsfactory.application.usecase.DetectBlenderVersion;
import com.physicsfactory.application.usecase.PrepareWorkspace;
import com.physicsfactory.application.usecase.RunBlenderHealthcheck;
import com.physicsfactory.application.usecase.ValidateBlenderInstallation;
import com.physicsfactory.domain.model.RenderRequest;
import com.physicsfactory.domain.model.RenderWorkspace;
import com.physicsfactory.domain.model.WorkspaceLayout;
import com.physicsfactory.infrastructure.blender.ClasspathBlenderScriptLibrary;
import com.physicsfactory.infrastructure.blender.JacksonSceneContractWriter;
import com.physicsfactory.infrastructure.blender.ProcessBlenderRunner;
import com.physicsfactory.infrastructure.bootstrap.BlenderHealthcheckRunner;
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
import org.springframework.core.annotation.Order;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

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

    /** The Blender-owned part of the workspace, kept apart from the user facing output folders. */
    @Bean
    RenderWorkspace renderWorkspace(WorkspaceLayout workspaceLayout) {
        return RenderWorkspace.of(workspaceLayout);
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
    BlenderScriptLibrary blenderScriptLibrary(RenderWorkspace renderWorkspace) {
        ResourcePatternResolver resourceResolver =
                new PathMatchingResourcePatternResolver(ClasspathBlenderScriptLibrary.class.getClassLoader());
        return new ClasspathBlenderScriptLibrary(renderWorkspace, resourceResolver);
    }

    @Bean
    SceneContractWriter sceneContractWriter() {
        return new JacksonSceneContractWriter();
    }

    /**
     * Blender runs with the workspace root as its working directory, so every relative path in a scene
     * contract means the same thing to Java and to Blender.
     */
    @Bean
    BlenderProcessRunner blenderProcessRunner(ValidateBlenderInstallation validateBlenderInstallation,
                                              PhysicsFactoryProperties properties,
                                              WorkspaceLayout workspaceLayout) {
        return new ProcessBlenderRunner(validateBlenderInstallation, properties.blender().executablePath(),
                workspaceLayout.root());
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
    DetectBlenderVersion detectBlenderVersion(BlenderProcessRunner blenderProcessRunner,
                                              PhysicsFactoryProperties properties) {
        return new DetectBlenderVersion(blenderProcessRunner, properties.blender().versionTimeout());
    }

    @Bean
    RunBlenderHealthcheck runBlenderHealthcheck(BlenderScriptLibrary blenderScriptLibrary,
                                                SceneContractWriter sceneContractWriter,
                                                BlenderProcessRunner blenderProcessRunner,
                                                RenderWorkspace renderWorkspace) {
        return new RunBlenderHealthcheck(blenderScriptLibrary, sceneContractWriter, blenderProcessRunner,
                renderWorkspace);
    }

    @Bean
    RenderRequest healthcheckRequest(PhysicsFactoryProperties properties) {
        PhysicsFactoryProperties.Healthcheck healthcheck = properties.blender().healthcheck();
        return new RenderRequest(healthcheck.template(), Path.of(healthcheck.outputFile()), healthcheck.timeout());
    }

    @Bean
    BootstrapEnvironment bootstrapEnvironment(PrepareWorkspace prepareWorkspace,
                                              BlenderScriptLibrary blenderScriptLibrary,
                                              ValidateBlenderInstallation validateBlenderInstallation,
                                              StartupReporter startupReporter) {
        return new BootstrapEnvironment(prepareWorkspace, blenderScriptLibrary, validateBlenderInstallation,
                startupReporter);
    }

    @Bean
    @Order(0)
    EnvironmentBootstrapRunner environmentBootstrapRunner(BootstrapEnvironment bootstrapEnvironment,
                                                          WorkspaceLayout workspaceLayout,
                                                          PhysicsFactoryProperties properties) {
        return new EnvironmentBootstrapRunner(bootstrapEnvironment, workspaceLayout,
                properties.blender().executablePath());
    }

    /** Runs after the bootstrap runner, which is what installs the script it executes. */
    @Bean
    @Order(10)
    BlenderHealthcheckRunner blenderHealthcheckRunner(DetectBlenderVersion detectBlenderVersion,
                                                      RunBlenderHealthcheck runBlenderHealthcheck,
                                                      RenderRequest healthcheckRequest) {
        return new BlenderHealthcheckRunner(detectBlenderVersion, runBlenderHealthcheck, healthcheckRequest);
    }

    @Bean
    ExitCodeExceptionMapper startupExitCodeMapper() {
        return new StartupExitCodeMapper();
    }
}
