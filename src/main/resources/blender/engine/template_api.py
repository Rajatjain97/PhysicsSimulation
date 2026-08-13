"""The interface every template implements.

This is the only thing the renderer knows about a template. A template that satisfies this interface
can be dropped into blender/templates and rendered without touching a single line of engine or Java
code - that is the whole point of the framework.

A template is built in five steps, always in the same order:

    configure_environment  -> world, background, backdrop
    create_objects         -> the content of the scene
    configure_camera       -> framing
    configure_lighting     -> key, fill, rim
    prepare_for_rendering  -> anything that must happen last, such as engine tuning

Subclasses override the steps they care about; the order lives in build() and is not theirs to
change. Future concerns - physics, animation, overlays, audio - arrive as new steps here, and every
existing template keeps working because the base class provides no-op defaults.
"""

import abc
from dataclasses import dataclass, field
from typing import Any, Dict

# Vertical is the product, not a template preference: Reels, Shorts and Spotlight are all portrait.
PORTRAIT_WIDTH = 1080
PORTRAIT_HEIGHT = 1920
PORTRAIT_FPS = 60


def frames_for(duration_seconds: float, fps: int) -> int:
    """Frame count for a duration. One definition, so the renderer and a physics cache agree."""
    return max(1, int(round(duration_seconds * fps)))


@dataclass(frozen=True)
class RenderSettings:
    """What the renderer needs from a template. Declared by the template, applied by the renderer.

    Duration is what makes a template a video rather than a still: leave it at zero and the renderer
    writes one image, set it and the renderer writes a movie of that length. Templates therefore
    choose their own natural duration and never touch a Blender output setting.
    """

    width: int = PORTRAIT_WIDTH
    height: int = PORTRAIT_HEIGHT
    samples: int = 96
    engine: str = "CYCLES"
    device: str = "CPU"
    file_format: str = "PNG"
    color_mode: str = "RGBA"
    fps: int = PORTRAIT_FPS
    duration_seconds: float = 0.0

    @property
    def resolution(self) -> str:
        return "{0}x{1}".format(self.width, self.height)

    @property
    def is_animation(self) -> bool:
        return self.duration_seconds > 0.0

    @property
    def frames(self) -> int:
        return frames_for(self.duration_seconds, self.fps)


@dataclass(frozen=True)
class TemplateContext:
    """Everything a template is given: its parameters and the shared asset library.

    Parameters come straight from the scene contract. Java never interprets them, so a template is
    free to define whatever it needs - marble count, palette, seed - without a Java change.

    Assets is the AssetRegistry: `context.assets.materials.resolve("DefaultGlass")`. It is typed
    loosely on purpose, so this module stays importable outside Blender - the registry reaches bpy,
    this file does not.
    """

    parameters: Dict[str, Any] = field(default_factory=dict)
    assets: Any = None

    def parameter(self, name: str, default: Any = None) -> Any:
        return self.parameters.get(name, default)


class Template(abc.ABC):
    """Base class for every template."""

    #: Name used in the scene contract, for example "DefaultSphere".
    name = ""

    #: One line describing the scene, surfaced by the registry.
    description = ""

    def build(self, context: TemplateContext) -> None:
        """Build the whole scene. The renderer calls only this."""
        self.configure_environment(context)
        self.create_objects(context)
        self.configure_camera(context)
        self.configure_lighting(context)
        self.prepare_for_rendering(context)

    def render_settings(self, context: TemplateContext) -> RenderSettings:
        """Portrait defaults; override to change resolution, sampling or engine."""
        return RenderSettings()

    @abc.abstractmethod
    def configure_environment(self, context: TemplateContext) -> None:
        """Reset the scene and set up world, background and backdrop."""

    @abc.abstractmethod
    def create_objects(self, context: TemplateContext) -> None:
        """Create the content of the scene."""

    @abc.abstractmethod
    def configure_camera(self, context: TemplateContext) -> None:
        """Place and frame the camera."""

    @abc.abstractmethod
    def configure_lighting(self, context: TemplateContext) -> None:
        """Light the scene."""

    def prepare_for_rendering(self, context: TemplateContext) -> None:
        """Optional final pass, after everything else exists."""
        return None
