package com.physicsfactory.application.port;

import com.physicsfactory.domain.model.EnvironmentReport;

/**
 * Outbound port for publishing startup information. The logging adapter implements it today; a
 * future desktop UI can add its own implementation without touching the use cases.
 */
public interface StartupReporter {

    void report(EnvironmentReport report);
}
