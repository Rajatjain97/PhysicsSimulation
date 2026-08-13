package com.physicsfactory.support;

import com.physicsfactory.application.port.StartupReporter;
import com.physicsfactory.domain.model.EnvironmentReport;
import java.util.ArrayList;
import java.util.List;

/** {@link StartupReporter} that keeps everything it was given. */
public final class RecordingStartupReporter implements StartupReporter {

    private final List<EnvironmentReport> reports = new ArrayList<>();

    @Override
    public void report(EnvironmentReport report) {
        reports.add(report);
    }

    public List<EnvironmentReport> reports() {
        return List.copyOf(reports);
    }
}
