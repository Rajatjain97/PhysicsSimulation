package com.physicsfactory.infrastructure.logging;

import com.physicsfactory.application.port.StartupReporter;
import com.physicsfactory.domain.model.EnvironmentReport;
import com.physicsfactory.domain.model.WorkspaceDirectory;
import com.physicsfactory.domain.model.WorkspaceLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes the startup summary through SLF4J, so it lands on the console and in the rolling log file
 * configured by {@code logback.xml}.
 */
public final class LoggingStartupReporter implements StartupReporter {

    private static final Logger log = LoggerFactory.getLogger(LoggingStartupReporter.class);

    private static final String SEPARATOR = "-".repeat(78);
    private static final String LABEL_FORMAT = "%-20s";

    private final String applicationName;
    private final String applicationVersion;

    public LoggingStartupReporter(String applicationName, String applicationVersion) {
        this.applicationName = Objects.requireNonNull(applicationName, "applicationName must not be null");
        this.applicationVersion = Objects.requireNonNull(applicationVersion, "applicationVersion must not be null");
    }

    @Override
    public void report(EnvironmentReport report) {
        Objects.requireNonNull(report, "report must not be null");
        WorkspaceLayout layout = report.workspace().layout();

        log.info(SEPARATOR);
        log.info("{} {} started", applicationName, applicationVersion);
        log.info(SEPARATOR);
        logValue("Java runtime", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        logValue("Operating system", System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
        logValue("Blender executable", report.blender().executable().toString());
        logValue("Workspace root", layout.root().toString());
        for (Map.Entry<WorkspaceDirectory, Path> directory : layout.directories().entrySet()) {
            logValue("  " + directory.getKey().configKey(), directory.getValue().toString());
        }
        logValue("Directories created", describeCreatedDirectories(report.workspace().createdDirectories()));
        logValue("Blender scripts", describeInstalledScripts(report.installedScripts()));
        log.info(SEPARATOR);
    }

    private void logValue(String label, String value) {
        log.info("{}: {}", String.format(LABEL_FORMAT, label), value);
    }

    private static String describeCreatedDirectories(List<Path> created) {
        if (created.isEmpty()) {
            return "none (workspace already provisioned)";
        }
        return created.size() + " (" + created.stream().map(Path::toString).collect(Collectors.joining(", ")) + ")";
    }

    private static String describeInstalledScripts(List<Path> scripts) {
        if (scripts.isEmpty()) {
            return "none";
        }
        return scripts.size() + " (" + scripts.stream()
                .map(script -> script.getFileName().toString())
                .collect(Collectors.joining(", ")) + ")";
    }
}
