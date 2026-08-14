"""What happens in a reel, and when.

A template describes a scene by building a timeline: spawn this, start physics there, hold the shot
for a while. The timeline says nothing about how any of it is achieved - it holds intent, not Blender
calls - which is what will let a later story execute the same timeline, and an AI director produce
one without knowing Blender exists.

Nothing here executes. This module is deliberately pure Python: no bpy, no imports from the rest of
the engine, no side effects. That is the whole point - a timeline can be built, compared, printed and
reasoned about anywhere.

An event is a kind, a time, an optional duration and a bag of intent:

    timeline.add(SPAWN_OBJECT, at=0.0, name="sphere", height=5.0)
    timeline.add(START_PHYSICS, at=0.0)
    timeline.add(WAIT, at=0.0, duration=10.0)

New event kinds - rotate a platform, open a gate, zoom the camera, show winner text - are new
constants and new keys in that bag. The model does not change shape to accommodate them.
"""

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Tuple

# Event kinds. Strings rather than a class hierarchy: an event is data, and a dozen near-empty
# subclasses would be a dozen files to touch every time the director learns a new trick.
SPAWN_OBJECT = "SpawnObject"
START_PHYSICS = "StartPhysics"
CAMERA_PRESET = "CameraPreset"
SHOW_TEXT = "ShowText"
WAIT = "Wait"

DEFAULT_TRACK = "main"


class TimelineError(Exception):
    """An event cannot be placed on a timeline."""


@dataclass(frozen=True)
class TimelineEvent:
    """One thing that happens, at one moment, on one track.

    :param kind:     what happens, one of the constants above
    :param at:       when it starts, in seconds from the beginning of the reel
    :param duration: how long it lasts, in seconds; None for an instant
    :param track:    which track it belongs to, for events that run alongside each other
    :param data:     intent for whoever executes it later - names, heights, presets
    :param sequence: insertion order, the tie-breaker that makes ordering deterministic
    """

    kind: str
    at: float
    duration: Optional[float] = None
    track: str = DEFAULT_TRACK
    data: Dict[str, Any] = field(default_factory=dict)
    sequence: int = 0

    def __post_init__(self):
        if not self.kind:
            raise TimelineError("An event needs a kind")
        if self.at < 0.0:
            raise TimelineError("Event '{0}' starts at {1}; time runs from zero".format(self.kind, self.at))
        if self.duration is not None and self.duration < 0.0:
            raise TimelineError("Event '{0}' has a negative duration".format(self.kind))

    @property
    def ends_at(self) -> float:
        return self.at + (self.duration or 0.0)

    def order_key(self) -> Tuple[float, str, int]:
        """Start time first, then track, then insertion order. Never anything unstable."""
        return (self.at, self.track, self.sequence)

    def __str__(self) -> str:
        window = "{0:.2f}s".format(self.at)
        if self.duration:
            window += "+{0:.2f}s".format(self.duration)
        return "{0}@{1}".format(self.kind, window)


class Timeline:
    """An ordered set of events. Built by a template, executed by a later story."""

    def __init__(self):
        self._events: List[TimelineEvent] = []

    def add(self, kind: str, at: float = 0.0, duration: Optional[float] = None,
            track: str = DEFAULT_TRACK, **data: Any) -> TimelineEvent:
        """Places an event and returns it.

        Insertion order is remembered, so two events at the same instant always come out in the order
        the template described them.
        """
        event = TimelineEvent(kind=kind, at=at, duration=duration, track=track,
                              data=dict(data), sequence=len(self._events))
        self._events.append(event)
        return event

    def events(self, track: Optional[str] = None) -> List[TimelineEvent]:
        """Every event in deterministic order, optionally limited to one track."""
        selected = self._events if track is None else [e for e in self._events if e.track == track]
        return sorted(selected, key=TimelineEvent.order_key)

    def tracks(self) -> List[str]:
        return sorted({event.track for event in self._events})

    def duration(self) -> float:
        """How long the reel runs: the end of the last thing that happens."""
        return max((event.ends_at for event in self._events), default=0.0)

    def is_empty(self) -> bool:
        return not self._events

    def __len__(self) -> int:
        return len(self._events)

    def __iter__(self):
        return iter(self.events())

    def summary(self) -> str:
        """One line for logs: '3 events over 10.00s [SpawnObject@0.00s, ...]'."""
        return "{0} events over {1:.2f}s [{2}]".format(
            len(self._events), self.duration(), ", ".join(str(event) for event in self.events()))
