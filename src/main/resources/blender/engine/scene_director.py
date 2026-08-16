"""Turns a timeline into a scene.

The director is the only thing that knows a timeline is meant to be carried out rather than merely
described. It walks the events in the timeline's own deterministic order and hands each one to the
handler registered for its kind - it does not know what spawning or physics involve, and it holds no
Blender code of its own.

That split is what makes new events cheap: a future RotatePlatform, OpenGate or CameraZoom is a
handler function and a registry entry, with nothing here to change. An event with no handler stops
the render with a clear message rather than being quietly skipped, because a silently missing event
produces a video that looks wrong for no visible reason.
"""

from dataclasses import dataclass, field
from typing import Any, Callable, Dict, Optional

import bpy

from .event_handlers import DEFAULT_HANDLERS


class UnhandledEventError(Exception):
    """The timeline contains an event kind no handler is registered for."""


@dataclass
class Stage:
    """What handlers are allowed to touch: the scene, the shared assets, the render settings.

    ``spawned`` is the one piece of state the director carries between events - what was created and
    what shape it was - so a physics event can attach the right collider to something an earlier
    spawn event made.

    ``content_end_frame`` is the second: the frame the simulation turned out to finish on. Until a
    physics event has run, nobody knows how long the reel is, so :attr:`frames` answers with what was
    requested and switches to the real length once the content has been simulated. Anything whose
    timing depends on the length of the reel - a camera move, a closing caption - must therefore be
    scheduled *after* the physics event on the timeline, which is the order a template describes
    things in anyway.
    """

    scene: Any
    assets: Any
    settings: Any
    spawned: Dict[str, str] = field(default_factory=dict)
    content_end_frame: Optional[int] = None
    settled: bool = True

    @property
    def frames(self) -> int:
        """How long the reel runs: the content, plus the hold, and never past the ceiling."""
        if self.content_end_frame is None:
            return self.settings.frames
        return min(self.settings.budget_frames, self.content_end_frame + self.settings.hold_frames)

    @property
    def duration_seconds(self) -> float:
        return self.frames / float(self.settings.fps)


@dataclass(frozen=True)
class Direction:
    """What carrying out a timeline produced: how many events ran, and how long the reel became."""

    events: int
    content_end_frame: Optional[int]
    settled: bool


class SceneDirector:
    """Executes a timeline against the current Blender scene."""

    def __init__(self, assets, settings, handlers: Optional[Dict[str, Callable]] = None):
        self._assets = assets
        self._settings = settings
        self._handlers = dict(DEFAULT_HANDLERS if handlers is None else handlers)

    def handled_kinds(self) -> list:
        return sorted(self._handlers)

    def direct(self, timeline) -> Direction:
        """Carries out every event, in order, and reports what the reel turned out to be.

        The scene is read at this point rather than at construction because a template resets it
        while building the environment.
        """
        stage = Stage(scene=bpy.context.scene, assets=self._assets, settings=self._settings)

        directed = 0
        for event in timeline.events():
            handler = self._handlers.get(event.kind)
            if handler is None:
                raise UnhandledEventError(
                    "No handler for event '{0}'. This engine can carry out: {1}".format(
                        event.kind, ", ".join(self.handled_kinds())))
            handler(event, stage)
            directed += 1
            print("director.event=" + str(event))
        return Direction(events=directed, content_end_frame=stage.content_end_frame,
                         settled=stage.settled)
