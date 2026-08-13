package com.physicsfactory.support;

import com.physicsfactory.application.port.BlenderScriptLibrary;
import com.physicsfactory.domain.exception.ScriptNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link BlenderScriptLibrary} backed by a fixed map, with no filesystem or classpath access. */
public final class InMemoryBlenderScriptLibrary implements BlenderScriptLibrary {

    private final Map<String, Path> scripts = new LinkedHashMap<>();
    private final List<String> lookups = new ArrayList<>();
    private int installations;

    public static InMemoryBlenderScriptLibrary containing(String scriptName, Path script) {
        InMemoryBlenderScriptLibrary library = new InMemoryBlenderScriptLibrary();
        library.scripts.put(scriptName, script);
        return library;
    }

    public static InMemoryBlenderScriptLibrary empty() {
        return new InMemoryBlenderScriptLibrary();
    }

    @Override
    public List<Path> installBundledScripts() {
        installations++;
        return List.copyOf(scripts.values());
    }

    @Override
    public Path locate(String scriptName) {
        lookups.add(scriptName);
        Path script = scripts.get(scriptName);
        if (script == null) {
            throw new ScriptNotFoundException(scriptName, Path.of("in-memory"));
        }
        return script;
    }

    public List<String> lookups() {
        return List.copyOf(lookups);
    }

    public int installations() {
        return installations;
    }
}
