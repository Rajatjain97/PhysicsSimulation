package com.physicsfactory.infrastructure.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.physicsfactory.domain.exception.BlenderNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;

class StartupValidationFailureAnalyzerTest {

    private final StartupValidationFailureAnalyzer analyzer = new StartupValidationFailureAnalyzer();

    @Test
    void describesTheProblemAndTheActionToTake() {
        BlenderNotFoundException cause = new BlenderNotFoundException("/opt/blender/blender");

        FailureAnalysis analysis = analyzer.analyze(new IllegalStateException("wrapper", cause));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).isEqualTo(cause.getMessage());
        assertThat(analysis.getAction()).isEqualTo(cause.remediation());
        assertThat(analysis.getCause()).isSameAs(cause);
    }

    @Test
    void ignoresUnrelatedFailures() {
        assertThat(analyzer.analyze(new IllegalStateException("unrelated"))).isNull();
    }
}
