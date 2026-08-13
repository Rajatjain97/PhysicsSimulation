package com.physicsfactory.infrastructure.blender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.physicsfactory.domain.exception.ScriptNotFoundException;
import com.physicsfactory.domain.model.RenderWorkspace;
import com.physicsfactory.support.WorkspaceLayouts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class ClasspathBlenderScriptLibraryTest {

    private static final String HEALTHCHECK = "healthcheck.py";

    @TempDir
    Path root;

    private RenderWorkspace workspace;
    private ClasspathBlenderScriptLibrary library;

    @BeforeEach
    void setUp() {
        workspace = RenderWorkspace.of(WorkspaceLayouts.rootedAt(root));
        library = new ClasspathBlenderScriptLibrary(workspace,
                new PathMatchingResourcePatternResolver(getClass().getClassLoader()));
    }

    @Test
    void installsTheBundledScriptsIntoTheRenderWorkspace() {
        List<Path> installed = library.installBundledScripts();

        assertThat(installed).isNotEmpty();
        assertThat(installed).allSatisfy(script -> assertThat(script).isRegularFile().hasParent(workspace.scripts()));
        assertThat(workspace.scripts().resolve(HEALTHCHECK)).isRegularFile();
        assertThat(Files.exists(workspace.scripts().resolve(HEALTHCHECK))).isTrue();
    }

    @Test
    void isIdempotentAndRepairsEditedScripts() throws IOException {
        library.installBundledScripts();
        Path healthcheck = workspace.scripts().resolve(HEALTHCHECK);
        String bundled = Files.readString(healthcheck);

        library.installBundledScripts();
        assertThat(Files.readString(healthcheck)).isEqualTo(bundled);

        Files.writeString(healthcheck, "print('edited by hand')");
        library.installBundledScripts();
        assertThat(Files.readString(healthcheck)).isEqualTo(bundled);
    }

    @Test
    void locatesAnInstalledScript() {
        library.installBundledScripts();

        assertThat(library.locate(HEALTHCHECK)).isEqualTo(workspace.scripts().resolve(HEALTHCHECK));
    }

    @Test
    void refusesScriptsThatAreNotInstalled() {
        library.installBundledScripts();

        assertThatThrownBy(() -> library.locate("marbles.py"))
                .isInstanceOf(ScriptNotFoundException.class)
                .hasMessageContaining("marbles.py");
    }

    @Test
    void refusesNamesThatWouldEscapeTheScriptDirectory() {
        library.installBundledScripts();

        assertThatThrownBy(() -> library.locate("../../etc/passwd")).isInstanceOf(ScriptNotFoundException.class);
        assertThatThrownBy(() -> library.locate("nested/healthcheck.py")).isInstanceOf(ScriptNotFoundException.class);
        assertThatThrownBy(() -> library.locate(root.resolve(HEALTHCHECK).toString()))
                .isInstanceOf(ScriptNotFoundException.class);
    }
}
