"""Rigid body physics for templates.

A template says what it wants - "this sphere is bouncy, that floor is solid, simulate the reel" - and
this module decides what that means to Blender: which operator adds a body, which properties carry
restitution, what the contact margin has to be, and how a simulation is actually made to run in
background mode.

That last part is the reason this layer exists at all. Getting a rigid body to move in a headless
render took four separate discoveries - the solver is not stepped during a background render, the
timestep comes from the scene frame rate, the point cache has to cover the frame range, and Blender's
default contact margin absorbs most of a bounce. Every future physics template would otherwise have
to rediscover all four.

Rigid bodies only. Cloth, fluids, particles and constraints are not this module's business.
"""

from dataclasses import dataclass
from typing import Dict, Optional

import bpy

# Body types.
ACTIVE = "ACTIVE"
PASSIVE = "PASSIVE"

# Collision shapes.
SPHERE = "SPHERE"
BOX = "BOX"
MESH = "MESH"

# Earth gravity, set explicitly rather than inherited, so a contract always simulates the same way.
GRAVITY = (0.0, 0.0, -9.81)

# Bullet resolves contacts inside a margin. Blender's 4cm default is enough to swallow most of a
# bounce, which reads as a ball that lands and sticks.
CONTACT_MARGIN = 0.001

# Accuracy of the solver. High enough for clean impacts, cheap enough that simulating a ten second
# reel is a second of work.
SUBSTEPS_PER_FRAME = 10
SOLVER_ITERATIONS = 20


class PhysicsError(Exception):
    """The requested physics configuration cannot be applied."""


class UnknownPhysicsPresetError(PhysicsError):
    """A template asked for a preset that does not exist."""


@dataclass(frozen=True)
class RigidBodyPreset:
    """How an object behaves when it is hit: what survives the impact, and how soon it settles.

    Deliberately small. Anything a template cannot express with these belongs in the template, not in
    a growing bag of Blender properties.
    """

    mass: float
    restitution: float
    friction: float
    linear_damping: float
    angular_damping: float


PRESETS: Dict[str, RigidBodyPreset] = {
    # Rubber-like and playful: keeps most of its energy, so a drop gives several clear bounces.
    "Bouncy": RigidBodyPreset(mass=1.0, restitution=0.8, friction=0.35,
                              linear_damping=0.02, angular_damping=0.08),
    # Dense and inert: one short bounce, then it stays where it landed.
    "Heavy": RigidBodyPreset(mass=8.0, restitution=0.25, friction=0.85,
                             linear_damping=0.1, angular_damping=0.25),
}

DEFAULT_PRESET = "Bouncy"


def preset_names() -> list:
    return sorted(PRESETS)


def preset(name: str) -> RigidBodyPreset:
    found = PRESETS.get(name)
    if found is None:
        raise UnknownPhysicsPresetError(
            'Unknown physics preset: "{0}". Available presets: {1}'.format(name, ", ".join(preset_names())))
    return found


class RigidBodyPhysics:
    """The physics API templates use. One instance per scene, created where it is needed."""

    def __init__(self, scene):
        self._scene = scene

    def add_dynamic(self, obj, shape: str = SPHERE, preset_name: str = DEFAULT_PRESET) -> None:
        """Makes an object fall, collide and bounce according to a preset."""
        settings = preset(preset_name)
        body = self._add_body(obj, ACTIVE)
        body.collision_shape = shape
        body.mass = settings.mass
        body.restitution = settings.restitution
        body.friction = settings.friction
        body.linear_damping = settings.linear_damping
        body.angular_damping = settings.angular_damping
        body.use_margin = True
        body.collision_margin = CONTACT_MARGIN
        # Let a body that has come to rest stop being simulated, so the reel ends in stillness
        # instead of a permanent shiver.
        body.use_deactivation = True
        body.deactivate_linear_velocity = 0.08
        body.deactivate_angular_velocity = 0.08

    def add_static(self, obj, shape: str = MESH) -> None:
        """Makes an object an immovable surface that other bodies collide with.

        A surface returns all the energy it is given: how much survives an impact is a property of
        the thing that hit it, which is what makes a dynamic body's preset the single control.
        """
        body = self._add_body(obj, PASSIVE)
        body.collision_shape = shape
        body.restitution = 1.0
        body.friction = 0.8
        body.use_margin = True
        body.collision_margin = CONTACT_MARGIN

    def simulate(self, frames: int, fps: int, tracked_name: Optional[str] = None) -> None:
        """Runs the whole simulation before anything is rendered.

        Three things have to be true first, and all three have bitten us:

        * The frame rate must already be the render frame rate, because the solver's timestep is one
          scene frame. Simulating at Blender's default 24 and playing back at 60 runs the reel two
          and a half times too fast.
        * The point cache must cover the frame range, or the motion stops partway through.
        * Something must actually advance the frames. A background render does not reliably step the
          solver, so every frame comes out with the object frozen at its starting transform.

        When a tracked object is named, the trajectory is summarised on stdout: a render is a slow
        way to find out that the physics was wrong.
        """
        scene = self._scene
        scene.render.fps = fps
        scene.render.fps_base = 1.0
        scene.frame_start = 1
        scene.frame_end = frames

        world = self._world()
        world.substeps_per_frame = SUBSTEPS_PER_FRAME
        world.solver_iterations = SOLVER_ITERATIONS
        world.point_cache.frame_start = 1
        world.point_cache.frame_end = frames

        tracked = bpy.data.objects.get(tracked_name) if tracked_name else None
        heights = []
        for frame in range(scene.frame_start, frames + 1):
            scene.frame_set(frame)
            if tracked is not None:
                heights.append(round(tracked.matrix_world.translation.z, 3))
        scene.frame_set(scene.frame_start)

        self._report(frames, heights)

    def _world(self):
        scene = self._scene
        if scene.rigidbody_world is None:
            bpy.ops.rigidbody.world_add()
        scene.use_gravity = True
        scene.gravity = GRAVITY
        return scene.rigidbody_world

    def _add_body(self, obj, body_type: str):
        self._world()
        # The operator works on the active object, so the caller does not have to have left it active.
        bpy.context.view_layer.objects.active = obj
        bpy.ops.rigidbody.object_add(type=body_type)
        if obj.rigid_body is None:
            raise PhysicsError("Blender did not attach a rigid body to '{0}'".format(obj.name))
        return obj.rigid_body

    @staticmethod
    def _report(frames: int, heights: list) -> None:
        if not heights:
            print("physics.simulated={0}".format(frames))
            return
        apexes = [heights[i] for i in range(1, len(heights) - 1)
                  if heights[i] > heights[i - 1] and heights[i] >= heights[i + 1]]
        print("physics.simulated={0} start={1} rest={2} bounces={3} apexes={4}".format(
            frames, heights[0], heights[-1], len(apexes), apexes[:5]))
