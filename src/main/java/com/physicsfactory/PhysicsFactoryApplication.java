package com.physicsfactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Physics Factory entry point.
 *
 * <p>Story 1.1 scope: prepare the local workspace, validate the Blender installation, report what was
 * found, and stop with a meaningful message when the environment is unusable.
 *
 * <p>Exit codes are defined in
 * {@link com.physicsfactory.infrastructure.diagnostics.StartupExitCodeMapper}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PhysicsFactoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhysicsFactoryApplication.class, args);
    }
}
