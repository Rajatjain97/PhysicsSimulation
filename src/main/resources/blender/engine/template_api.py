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
from dataclasses import dataclass, field, replace
from typing import Any, Dict

from .timeline import Timeline

# Vertical is the product, not a template preference: Reels, Shorts and Spotlight are all portrait.
PORTRAIT_WIDTH = 1080
PORTRAIT_HEIGHT = 1920
PORTRAIT_FPS = 60


#: How long the reel holds after its content has finished, so a video does not cut on the last event.
DEFAULT_HOLD_SECONDS = 1.5

#: The longest a reel may ever become. Content decides when a video ends, so something has to stop a
#: simulation that never settles from rendering forever; the vertical formats top out well below this.
MAX_REEL_SECONDS = 30.0


def frames_for(duration_seconds: float, fps: int) -> int:
    """Frame count for a duration. One definition, so the renderer and a physics cache agree."""
    return max(1, int(round(duration_seconds * fps)))


@dataclass(frozen=True)
class RenderSettings:
    """What the renderer needs from a template. Declared by the template, applied by the renderer.

    Duration is what makes a template a video rather than a still: leave it at zero and the renderer
    writes one image, set it and the renderer writes a movie. Templates therefore choose their own
    natural duration and never touch a Blender output setting.

    ``duration_seconds`` is the duration that was *asked for*, and it is a target rather than a cut.
    A reel of falling objects is finished when the objects have finished falling, not when a number
    ran out, so what it really controls is how long the template schedules its content over. The
    video itself ends at content completion plus :attr:`hold_seconds`, and
    :attr:`max_duration_seconds` is the ceiling that stops a simulation which never settles.
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
    hold_seconds: float = DEFAULT_HOLD_SECONDS
    max_duration_seconds: float = MAX_REEL_SECONDS

    @property
    def resolution(self) -> str:
        return "{0}x{1}".format(self.width, self.height)

    @property
    def is_animation(self) -> bool:
        return self.duration_seconds > 0.0

    @property
    def frames(self) -> int:
        """Frames the template plans its content over: the requested duration, nothing more."""
        return frames_for(self.duration_seconds, self.fps)

    @property
    def hold_frames(self) -> int:
        return frames_for(self.hold_seconds, self.fps) if self.hold_seconds > 0.0 else 0

    @property
    def budget_frames(self) -> int:
        """The most frames a simulation may run for. Never shorter than what was requested."""
        return max(self.frames, frames_for(self.max_duration_seconds, self.fps))

    def with_limits(self, hold_seconds: float, max_duration_seconds: float) -> "RenderSettings":
        """A copy carrying operator-supplied duration limits. Frozen, so this returns a new one."""
        return replace(self, hold_seconds=max(0.0, float(hold_seconds)),
                       max_duration_seconds=max(float(max_duration_seconds), self.duration_seconds))


@dataclass(frozen=True)
class DurationPlan:
    """The duration decision, in one object: what was asked for, what the content needed, what ran.

    Built once the content has actually been simulated and then carried to everything downstream, so
    the renderer, the manifest and the log all state the same numbers rather than each deriving their
    own. ``settled`` records whether the content genuinely finished or the reel ceiling cut it off -
    the one case where a video really is truncated, and one worth being able to see afterwards.
    """

    fps: int
    requested_seconds: float
    content_frames: int
    hold_frames: int
    settled: bool = True

    @property
    def frames(self) -> int:
        return max(1, self.content_frames + self.hold_frames)

    @property
    def content_seconds(self) -> float:
        return round(self.content_frames / float(self.fps), 3)

    @property
    def hold_seconds(self) -> float:
        return round(self.hold_frames / float(self.fps), 3)

    @property
    def seconds(self) -> float:
        return round(self.frames / float(self.fps), 3)

    def summary(self) -> str:
        return ("requested={0:.2f}s content={1:.2f}s hold={2:.2f}s final={3:.2f}s frames={4} "
                "settled={5}").format(self.requested_seconds, self.content_seconds, self.hold_seconds,
                                      self.seconds, self.frames, str(self.settled).lower())


@dataclass(frozen=True)
class TemplateContext:
    """Everything a template is given: its parameters and the shared asset library.

    Parameters come straight from the scene contract. Java never interprets them, so a template is
    free to define whatever it needs - marble count, palette, seed - without a Java change.

    Assets is the AssetRegistry: `context.assets.materials.resolve("DefaultGlass")`. It is typed
    loosely on purpose, so this module stays importable outside Blender - the registry reaches bpy,
    this file does not.

    Random is the RandomContext for this render: `context.random.stream("placement")`. Every random
    decision a template makes comes from there, so the same seed always rebuilds the same scene.
    """

    parameters: Dict[str, Any] = field(default_factory=dict)
    assets: Any = None
    random: Any = None

    def parameter(self, name: str, default: Any = None) -> Any:
        return self.parameters.get(name, default)


def number(context: "TemplateContext", name: str, default: float) -> float:
    """Reads a numeric parameter.

    Parameters arrive from JSON, so a number may be written as a string. Fail clearly if it is not a
    number at all: a template that silently substitutes a default produces a reel nobody asked for.
    """
    value = context.parameter(name, default)
    try:
        return float(value)
    except (TypeError, ValueError):
        raise ValueError("Parameter '{0}' must be a number but was {1!r}".format(name, value))


def positive_number(context: "TemplateContext", name: str, default: float) -> float:
    """Reads a numeric parameter that would be meaningless at zero or below - a count, a height."""
    value = number(context, name, default)
    if value <= 0.0:
        raise ValueError("Parameter '{0}' must be greater than zero but was {1}".format(name, value))
    return value


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

    def timeline(self, context: TemplateContext) -> Timeline:
        """What happens in this scene and when, as intent rather than Blender calls.

        The scene director carries this out, so this is where a template's content belongs: objects,
        physics, framing and captions. A template that describes nothing here returns an empty
        timeline and is built entirely by :meth:`build`, which is what a still image needs.
        """
        return Timeline()

    @abc.abstractmethod
    def configure_environment(self, context: TemplateContext) -> None:
        """Reset the scene and set up world, background and backdrop."""

    def create_objects(self, context: TemplateContext) -> None:
        """Create the content of the scene.

        Optional: a template that describes its objects in :meth:`timeline` leaves this alone and
        lets the scene director spawn them.
        """
        return None

    def configure_camera(self, context: TemplateContext) -> None:
        """Place and frame the camera.

        Optional, for the same reason as :meth:`create_objects` - a camera preset event does it.
        """
        return None

    @abc.abstractmethod
    def configure_lighting(self, context: TemplateContext) -> None:
        """Light the scene."""

    def prepare_for_rendering(self, context: TemplateContext) -> None:
        """Optional final pass, after everything else exists."""
        return None
