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

from .physics import BOX, MESH, SPHERE, RigidBodyPhysics
from .timeline import CAMERA_PRESET, SPAWN_OBJECT, START_PHYSICS, WAIT

# Mesh quality for spawned primitives. Not scene intent, so not in the timeline.
SPHERE_SEGMENTS = 64
SPHERE_RINGS = 32
DEFAULT_PLANE_SIZE = 60.0
DEFAULT_SPHERE_RADIUS = 0.6

# How a spawned shape collides, once something asks it to take part in physics.
COLLISION_SHAPES = {
    "sphere": SPHERE,
    "box": BOX,
    "plane": MESH,
}

# Camera framing presets. A preset is a way of composing a shot, named by intent rather than by lens
# millimetres, so a timeline never carries camera mathematics.
PORTRAIT_SENSOR_HALF_WIDTH = 18.0
PORTRAIT_CAMERA_DISTANCE = 9.0
PORTRAIT_MIN_LENS = 24.0
PORTRAIT_MAX_LENS = 85.0
PORTRAIT_MARGIN = 1.8


class EventHandlerError(Exception):
    """An event cannot be carried out as described."""


def spawn_object(event, stage) -> None:
    """Creates one object, gives it a material, and optionally makes it a fixed surface."""
    name = event.data.get("name")
    shape = event.data.get("shape")
    if not name or not shape:
        raise EventHandlerError("A spawn event needs a name and a shape: " + str(event.data))

    if shape == "sphere":
        bpy.ops.mesh.primitive_uv_sphere_add(
            radius=float(event.data.get("radius", DEFAULT_SPHERE_RADIUS)),
            location=(0.0, 0.0, float(event.data.get("height", 0.0))),
            segments=SPHERE_SEGMENTS, ring_count=SPHERE_RINGS)
        bpy.ops.object.shade_smooth()
    elif shape == "plane":
        bpy.ops.mesh.primitive_plane_add(size=float(event.data.get("size", DEFAULT_PLANE_SIZE)),
                                         location=(0.0, 0.0, float(event.data.get("height", 0.0))))
    else:
        raise EventHandlerError(
            "Unknown shape '{0}'; this engine can spawn: {1}".format(shape, ", ".join(sorted(COLLISION_SHAPES))))

    spawned = bpy.context.active_object
    spawned.name = name
    material = event.data.get("material")
    if material:
        spawned.data.materials.append(stage.assets.materials.resolve(str(material)))

    # Remembered so a later StartPhysics knows what it is attaching a body to.
    stage.spawned[name] = shape

    if event.data.get("physics") == "static":
        RigidBodyPhysics(stage.scene).add_static(spawned, shape=_collision_shape(shape))


def start_physics(event, stage) -> None:
    """Makes an object dynamic and simulates the whole reel.

    The simulation length comes from the render settings, so a reel is always simulated for exactly
    as long as it is shown.
    """
    target_name = event.data.get("target")
    if not target_name:
        raise EventHandlerError("A physics event needs a target: " + str(event.data))
    target = bpy.data.objects.get(target_name)
    if target is None:
        raise EventHandlerError("Cannot start physics: nothing called '{0}' was spawned".format(target_name))

    physics = RigidBodyPhysics(stage.scene)
    physics.add_dynamic(target,
                        shape=_collision_shape(stage.spawned.get(target_name, "sphere")),
                        preset_name=str(event.data.get("preset", "Bouncy")))
    physics.simulate(stage.settings.frames, stage.settings.fps, tracked_name=target_name)


def camera_preset(event, stage) -> None:
    """Places the camera using a named framing."""
    framing = str(event.data.get("framing", "portrait-drop"))
    if framing != "portrait-drop":
        raise EventHandlerError(
            "Unknown camera framing '{0}'; this engine knows: portrait-drop".format(framing))

    # Fit the requested vertical extent plus a margin of air, then derive the lens from it.
    coverage = float(event.data.get("covers", 0.0)) + PORTRAIT_MARGIN
    lens = min(PORTRAIT_MAX_LENS,
               max(PORTRAIT_MIN_LENS,
                   (2.0 * PORTRAIT_SENSOR_HALF_WIDTH * PORTRAIT_CAMERA_DISTANCE) / coverage))

    camera_data = bpy.data.cameras.new("StudioCamera")
    camera_data.lens = lens
    camera = bpy.data.objects.new("StudioCamera", camera_data)
    camera.location = (0.0, -PORTRAIT_CAMERA_DISTANCE, coverage / 2.0 - 0.6)
    camera.rotation_euler = (math.radians(87.0), 0.0, 0.0)
    stage.scene.collection.objects.link(camera)
    stage.scene.camera = camera


def wait(event, stage) -> None:
    """Holds the shot. A render has no clock to wait on - the duration is the reel's length."""
    return None


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
    WAIT: wait,
}
