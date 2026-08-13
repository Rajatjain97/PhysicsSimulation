package com.physicsfactory.support;

import com.physicsfactory.application.port.BlenderProcessRunner;
import com.physicsfactory.domain.model.BlenderExecution;
import com.physicsfactory.domain.model.BlenderScriptRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** {@link BlenderProcessRunner} that answers with a pre-programmed execution and records its calls. */
public final class StubBlenderProcessRunner implements BlenderProcessRunner {

    private final BlenderExecution versionResponse;
    private final BlenderExecution scriptResponse;
    private final List<BlenderScriptRequest> scriptRequests = new ArrayList<>();
    private final List<Duration> versionProbes = new ArrayList<>();

    private StubBlenderProcessRunner(BlenderExecution versionResponse, BlenderExecution scriptResponse) {
        this.versionResponse = Objects.requireNonNull(versionResponse, "versionResponse must not be null");
        this.scriptResponse = Objects.requireNonNull(scriptResponse, "scriptResponse must not be null");
    }

    public static StubBlenderProcessRunner answering(BlenderExecution response) {
        return new StubBlenderProcessRunner(response, response);
    }

    /** Answers the version probe and script runs differently. */
    public static StubBlenderProcessRunner answering(BlenderExecution versionResponse, BlenderExecution scriptResponse) {
        return new StubBlenderProcessRunner(versionResponse, scriptResponse);
    }

    public static BlenderExecution execution(int exitCode, String standardOutput, String standardError) {
        return new BlenderExecution(List.of("blender"), exitCode, standardOutput, standardError, Duration.ofMillis(100));
    }

    public static StubBlenderProcessRunner succeedingWith(String standardOutput) {
        return answering(new BlenderExecution(List.of("blender"), 0, standardOutput, "", Duration.ofMillis(120)));
    }

    public static StubBlenderProcessRunner failingWith(int exitCode, String standardError) {
        return answering(new BlenderExecution(List.of("blender"), exitCode, "", standardError, Duration.ofMillis(90)));
    }

    @Override
    public BlenderExecution runVersionProbe(Duration timeout) {
        versionProbes.add(timeout);
        return versionResponse;
    }

    @Override
    public BlenderExecution runScript(BlenderScriptRequest request) {
        scriptRequests.add(request);
        return scriptResponse;
    }

    public List<BlenderScriptRequest> scriptRequests() {
        return List.copyOf(scriptRequests);
    }

    public List<Duration> versionProbes() {
        return List.copyOf(versionProbes);
    }
}
