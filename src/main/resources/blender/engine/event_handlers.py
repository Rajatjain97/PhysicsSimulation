"""What each kind of timeline event actually does in Blender.

The director decides *when* handlers run; this module is *how* they run. Splitting the two is what
keeps the director free of Blender and free of a growing switch statement: a new event kind is a new
function and one entry in the registry at the bottom, and nothing else in the engine changes.

A handler takes the event and the stage - the scene, the asset library, the render settings - and
returns nothing. It may read what earlier handlers put on the stage, which is how physics knows what
shape the object it is being attached to was spawned as.
"""

import math

import bpy

from .camera import DEFAULT_PRESET as DEFAULT_CAMERA_PRESET
from .camera import CameraRequest, place as place_camera
from .physics import BOX, MESH, SPHERE, RigidBodyPhysics
from .text_overlay import TextRequest, show as show_caption
from .timeline import CAMERA_PRESET, SHOW_TEXT, SPAWN_OBJECT, START_PHYSICS, WAIT

# Mesh quality for spawned primitives. Not scene intent, so not in the timeline.
SPHERE_SEGMENTS = 64
SPHERE_RINGS = 32
DEFAULT_PLANE_SIZE = 60.0
DEFAULT_SPHERE_RADIUS = 0.6
DEFAULT_BOX_SIZE = 2.0

# How a spawned shape collides, once something asks it to take part in physics.
COLLISION_SHAPES = {
    "sphere": SPHERE,
    "box": BOX,
    "plane": MESH,
}


class EventHandlerError(Exception):
    """An event cannot be carried out as described."""


def spawn_object(event, stage) -> None:
    """Creates one object, gives it a material, and optionally makes it a fixed surface."""
    name = event.data.get("name")
    shape = event.data.get("shape")
    if not name or not shape:
        raise EventHandlerError("A spawn event needs a name and a shape: " + str(event.data))

    location = _location(event.data)
    if shape == "sphere":
        bpy.ops.mesh.primitive_uv_sphere_add(
            radius=float(event.data.get("radius", DEFAULT_SPHERE_RADIUS)), location=location,
            segments=SPHERE_SEGMENTS, ring_count=SPHERE_RINGS)
        bpy.ops.object.shade_smooth()
    elif shape == "plane":
        bpy.ops.mesh.primitive_plane_add(size=float(event.data.get("size", DEFAULT_PLANE_SIZE)),
                                         location=location)
    elif shape == "box":
        bpy.ops.mesh.primitive_cube_add(size=float(event.data.get("size", DEFAULT_BOX_SIZE)),
                                        location=location)
    else:
        raise EventHandlerError(
            "Unknown shape '{0}'; this engine can spawn: {1}".format(shape, ", ".join(sorted(COLLISION_SHAPES))))

    spawned = bpy.context.active_object
    spawned.name = name

    rotation = event.data.get("rotation")
    if rotation:
        # Degrees in the timeline: intent stays readable, radians are a Blender detail.
        spawned.rotation_euler = tuple(math.radians(float(angle)) for angle in rotation)
    scale = event.data.get("scale")
    if scale:
        spawned.scale = tuple(float(axis) for axis in scale)

    if event.data.get("visible") is False:
        # A collider the camera should not see: a board needs a front wall to keep objects in its
        # plane, and that wall must not stand between the camera and the scene.
        spawned.hide_render = True

    _apply_material(spawned, event.data, stage)

    # Remembered so a later StartPhysics knows what it is attaching a body to.
    stage.spawned[name] = shape

    if event.data.get("physics") == "static":
        RigidBodyPhysics(stage.scene).add_static(spawned, shape=_collision_shape(shape))


def start_physics(event, stage) -> None:
    """Makes one or many objects dynamic, then simulates the whole reel once.

    A scene with twenty-five marbles is one physics event with twenty-five targets, not twenty-five
    events: the bodies all have to exist before anything is simulated, and simulating once is the
    difference between a reel and twenty-five reels.

    The simulation is what decides how long the reel is. It runs until these bodies have stopped
    moving - not until a requested duration runs out - and the frame that happened on is left on the
    stage for everything scheduled afterwards. Nothing is nudged, keyframed or removed to make that
    moment arrive sooner.
    """
    names = event.data.get("targets") or ([event.data.get("target")] if event.data.get("target") else [])
    if not names:
        raise EventHandlerError("A physics event needs a target or targets: " + str(event.data))

    physics = RigidBodyPhysics(stage.scene)
    preset_name = str(event.data.get("preset", "Bouncy"))
    for target_name in names:
        target = bpy.data.objects.get(target_name)
        if target is None:
            raise EventHandlerError(
                "Cannot start physics: nothing called '{0}' was spawned".format(target_name))
        physics.add_dynamic(target,
                            shape=_collision_shape(stage.spawned.get(target_name, "sphere")),
                            preset_name=preset_name)

    settings = stage.settings
    outcome = physics.simulate(settings.budget_frames, settings.fps, settings.frames,
                               hold_frames=settings.hold_frames, tracked_names=names)
    stage.content_end_frame = outcome.content_end_frame
    stage.settled = outcome.settled


def camera_preset(event, stage) -> None:
    """Sets up the shot the timeline asked for. The framing itself lives in engine.camera."""
    request = CameraRequest(
        preset=str(event.data.get("preset", DEFAULT_CAMERA_PRESET)),
        covers=float(event.data.get("covers", 0.0)),
        target=event.data.get("target"),
        frames=stage.frames,
        fps=stage.settings.fps,
        options={key: value for key, value in event.data.items()
                 if key not in ("preset", "covers", "target")})
    place_camera(request, stage.scene)


def show_text(event, stage) -> None:
    """Puts a caption on screen for the window the timeline gave it.

    Timing comes from the event itself - when it starts and how long it lasts - so a caption is
    scheduled exactly like anything else on the timeline.

    A closing caption is the exception: it belongs at the end of the reel, and with a content-driven
    duration nobody knows when that is while the timeline is being written. ``anchor="end"`` says so,
    and the caption is placed against the length the reel actually turned out to be.
    """
    at = event.at
    if str(event.data.get("anchor", "")).lower() == "end":
        at = max(0.0, stage.duration_seconds - (event.duration or 0.0))

    request = TextRequest(
        text=str(event.data.get("text", "")),
        style=str(event.data.get("style", "Default")),
        position=event.data.get("position"),
        at=at,
        duration=event.duration,
        name=event.data.get("name"))
    show_caption(request, stage.scene, stage.settings.fps, stage.frames)


def wait(event, stage) -> None:
    """Holds the shot. A render has no clock to wait on - the duration is the reel's length."""
    return None


def _location(data) -> tuple:
    """Either a full position, or just a height for the common case of something on the axis."""
    location = data.get("location")
    if location:
        return tuple(float(axis) for axis in location)
    return (0.0, 0.0, float(data.get("height", 0.0)))


def _apply_material(spawned, data, stage) -> None:
    material = data.get("material")
    if not material:
        return
    tint = data.get("tint")
    if tint:
        resolved = stage.assets.materials.resolve_variant(
            str(material), str(data.get("tintName", "Tinted")), tint)
    else:
        resolved = stage.assets.materials.resolve(str(material))
    spawned.data.materials.append(resolved)


def _collision_shape(shape: str) -> str:
    collision = COLLISION_SHAPES.get(shape)
    if collision is None:
        raise EventHandlerError(
            "Shape '{0}' cannot take part in physics; known shapes: {1}".format(
                shape, ", ".join(sorted(COLLISION_SHAPES))))
    return collision


#: The handlers the director uses unless it is given others. Adding an event kind is one entry here.
DEFAULT_HANDLERS = {
    SPAWN_OBJECT: spawn_object,
    START_PHYSICS: start_physics,
    CAMERA_PRESET: camera_preset,
    SHOW_TEXT: show_text,
    WAIT: wait,
}
