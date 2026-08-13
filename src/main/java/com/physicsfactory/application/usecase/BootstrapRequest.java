package com.physicsfactory.application.usecase;

import com.physicsfactory.domain.model.WorkspaceLayout;
import java.util.Objects;

/**
 * Input for {@link BootstrapEnvironment}.
 *
 * @param workspace                 the layout to provision
 * @param blenderExecutableLocation the configured Blender location, exactly as the operator wrote it
 */
public record BootstrapRequest(WorkspaceLayout workspace, String blenderExecutableLocation) {

    public BootstrapRequest {
        Objects.requireNonNull(workspace, "workspace must not be null");
        Objects.requireNonNull(blenderExecutableLocation, "blenderExecutableLocation must not be null");
    }
}
