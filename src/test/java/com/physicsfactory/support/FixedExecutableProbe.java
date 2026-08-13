package com.physicsfactory.support;

import com.physicsfactory.application.port.ExecutableProbe;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@link ExecutableProbe} with a fixed, pre-programmed answer. */
public final class FixedExecutableProbe implements ExecutableProbe {

    private final Map<String, Path> resolvable;
    private final List<String> requests = new ArrayList<>();

    private FixedExecutableProbe(Map<String, Path> resolvable) {
        this.resolvable = Map.copyOf(resolvable);
    }

    public static FixedExecutableProbe resolvingNothing() {
        return new FixedExecutableProbe(Map.of());
    }

    public static FixedExecutableProbe resolving(String configuredLocation, Path resolved) {
        return new FixedExecutableProbe(Map.of(configuredLocation, resolved));
    }

    @Override
    public Optional<Path> resolve(String configuredLocation) {
        requests.add(configuredLocation);
        return Optional.ofNullable(resolvable.get(configuredLocation));
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }
}
