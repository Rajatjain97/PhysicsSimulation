"""How a shot is framed.

A template asks for a camera by name - Static, Orbit, FollowObject - and this module knows what that
means in Blender: where the camera goes, what lens it needs, whether it tracks something and whether
it moves. Nothing else in the engine does camera mathematics.

Every preset is a function of the same request, registered in PRESETS at the bottom. Adding
CinematicOrbit, Crane, FlyThrough or FocusPull is a function and one entry; no other file changes.

Two rules keep the results watchable rather than merely correct. Framing is derived from the vertical
extent the scene says it needs, so nothing is ever cropped when a parameter changes. And movement is
either constant or eased across the whole reel - never per-frame decisions - so the camera cannot
jitter.
"""

import math
from dataclasses import dataclass, field
from typing import Any, Dict, Optional

import bpy

CAMERA_NAME = "StudioCamera"

# Portrait framing. The sensor is 36mm, so half of it is what the field of view is built from.
SENSOR_HALF_WIDTH = 18.0
DEFAULT_DISTANCE = 9.0
MIN_LENS = 24.0
MAX_LENS = 85.0
VERTICAL_MARGIN = 1.8

# Gentle by default: a push-in of a few percent over the whole reel reads as intent, not movement.
DEFAULT_ZOOM = 1.12
DEFAULT_TURNS = 1.0
ORBIT_SAMPLES_PER_TURN = 72


class CameraError(Exception):
    """The requested shot cannot be set up."""


@dataclass(frozen=True)
class CameraRequest:
    """What the shot needs to know.

    :param preset:  which framing to use
    :param covers:  the vertical extent that must stay in frame, in metres
    :param target:  object to look at, for the presets that track something
    :param frames:  length of the reel, for the presets that move
    :param fps:     frame rate, for the presets that move
    :param options: preset-specific knobs from the timeline event
    """

    preset: str
    covers: float = 0.0
    target: Optional[str] = None
    frames: int = 1
    fps: int = 60
    options: Dict[str, Any] = field(default_factory=dict)

    def option(self, name: str, default: Any) -> Any:
        return self.options.get(name, default)

    @property
    def coverage(self) -> float:
        """The framed height: what has to be visible, plus air around it."""
        return max(self.covers, 0.0) + VERTICAL_MARGIN


def place(request: CameraRequest, scene):
    """Creates the scene camera for a request and returns it."""
    preset = PRESETS.get(request.preset)
    if preset is None:
        raise CameraError("Unknown camera preset '{0}'. This engine knows: {1}".format(
            request.preset, ", ".join(preset_names())))

    camera_data = bpy.data.cameras.new(CAMERA_NAME)
    camera = bpy.data.objects.new(CAMERA_NAME, camera_data)
    scene.collection.objects.link(camera)
    scene.camera = camera

    preset(camera, request)
    return camera


def preset_names() -> list:
    return sorted(PRESETS)


# --- presets ------------------------------------------------------------------------------------

def static(camera, request: CameraRequest) -> None:
    """Head-on portrait shot that fits the whole action. The default, and the steadiest."""
    coverage = request.coverage
    camera.data.lens = _lens_for(coverage)
    camera.location = (0.0, -DEFAULT_DISTANCE, coverage / 2.0 - 0.6)
    camera.rotation_euler = (math.radians(87.0), 0.0, 0.0)


def top_down(camera, request: CameraRequest) -> None:
    """Overhead view, looking straight down. A camera's default axis is already -Z."""
    coverage = request.coverage
    camera.data.lens = _lens_for(coverage)
    camera.location = (0.0, 0.0, max(coverage, DEFAULT_DISTANCE))
    camera.rotation_euler = (0.0, 0.0, 0.0)


def follow_object(camera, request: CameraRequest) -> None:
    """Static position, but aimed at a named object for the whole reel.

    A tracking constraint rather than per-frame aiming: Blender re-evaluates it as the target moves,
    so the object stays centred without a single keyframe and without any chance of jitter.
    """
    static(camera, request)
    _track(camera, _require_target(request))


def orbit(camera, request: CameraRequest) -> None:
    """Circles the target at a constant rate, staying aimed at it.

    Constant angular speed with linear interpolation: any easing between samples would read as the
    camera hesitating on its way round.
    """
    target = _require_target(request)
    coverage = request.coverage
    camera.data.lens = _lens_for(coverage)
    height = coverage / 2.0 - 0.6
    turns = float(request.option("turns", DEFAULT_TURNS))
    radius = float(request.option("radius", DEFAULT_DISTANCE))

    samples = max(2, int(round(ORBIT_SAMPLES_PER_TURN * abs(turns))))
    for sample in range(samples + 1):
        progress = sample / float(samples)
        frame = 1 + int(round(progress * max(request.frames - 1, 1)))
        angle = 2.0 * math.pi * turns * progress
        camera.location = (radius * math.sin(angle), -radius * math.cos(angle), height)
        camera.keyframe_insert(data_path="location", frame=frame)

    _interpolate(camera, "LINEAR")
    _track(camera, target)


def slow_zoom(camera, request: CameraRequest) -> None:
    """Static shot with a slow push-in.

    The lens moves rather than the camera, so nothing in the scene shifts perspective - and the
    default easing means the move starts and ends softly.
    """
    static(camera, request)
    zoom = float(request.option("zoom", DEFAULT_ZOOM))
    start_lens = camera.data.lens

    camera.data.lens = start_lens
    camera.data.keyframe_insert(data_path="lens", frame=1)
    camera.data.lens = min(MAX_LENS, start_lens * zoom)
    camera.data.keyframe_insert(data_path="lens", frame=max(request.frames, 2))


PRESETS = {
    "Static": static,
    "TopDown": top_down,
    "FollowObject": follow_object,
    "Orbit": orbit,
    "SlowZoom": slow_zoom,
}

DEFAULT_PRESET = "Static"


# --- shared mechanics ---------------------------------------------------------------------------

def _lens_for(coverage: float) -> float:
    """The lens that fits a vertical extent at the working distance, kept within sane focal lengths."""
    if coverage <= 0.0:
        raise CameraError("A shot needs something to frame; 'covers' was {0}".format(coverage))
    return min(MAX_LENS, max(MIN_LENS, (2.0 * SENSOR_HALF_WIDTH * DEFAULT_DISTANCE) / coverage))


def _require_target(request: CameraRequest):
    if not request.target:
        raise CameraError("Camera preset '{0}' needs a target to look at".format(request.preset))
    target = bpy.data.objects.get(request.target)
    if target is None:
        raise CameraError("Camera cannot follow '{0}': no such object in the scene".format(request.target))
    return target


def _track(camera, target) -> None:
    constraint = camera.constraints.new(type="TRACK_TO")
    constraint.target = target
    constraint.track_axis = "TRACK_NEGATIVE_Z"
    constraint.up_axis = "UP_Y"


def _interpolate(camera, mode: str) -> None:
    animation = getattr(camera, "animation_data", None)
    action = getattr(animation, "action", None) if animation else None
    if action is None:
        return
    for curve in action.fcurves:
        for keyframe in curve.keyframe_points:
            keyframe.interpolation = mode
