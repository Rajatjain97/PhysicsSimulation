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


@dataclass(frozen=True)
class RenderSettings:
    """What the renderer needs from a template. Declared by the template, applied by the renderer."""

    width: int = PORTRAIT_WIDTH
    height: int = PORTRAIT_HEIGHT
    samples: int = 96
    engine: str = "CYCLES"
    device: str = "CPU"
    file_format: str = "PNG"
    color_mode: str = "RGBA"

    @property
    def resolution(self) -> str:
        return "{0}x{1}".format(self.width, self.height)


@dataclass(frozen=True)
class TemplateContext:
    """Everything a template is given: its parameters, and nothing else.

    Parameters come straight from the scene contract. Java never interprets them, so a template is
    free to define whatever it needs - marble count, palette, seed - without a Java change.
    """

    parameters: Dict[str, Any] = field(default_factory=dict)

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
