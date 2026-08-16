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

from .timing import measure

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

# When the content is considered finished: every simulated body moving slower than this, for this
# long. The speed sits just under the solver's own deactivation threshold, so a body Blender has put
# to sleep always counts as still, while a marble still creeping towards a bucket does not. Half a
# second of stillness rather than a single frame, because a ball at the top of a bounce is motionless
# for an instant and has not finished anything.
REST_SPEED = 0.06
REST_SECONDS = 0.5


class PhysicsError(Exception):
    """The requested physics configuration cannot be applied."""


class UnknownPhysicsPresetError(PhysicsError):
    """A template asked for a preset that does not exist."""


@dataclass(frozen=True)
class SimulationOutcome:
    """What the simulation turned out to need.

    ``content_end_frame`` is the earliest frame at which nothing meaningful is moving any more. It is
    measured, not predicted: the simulation is stepped frame by frame anyway, so the engine watches
    what the bodies actually do rather than guessing from a formula that would be wrong for every
    template in a different way.

    ``settled`` is false when the bodies were still moving when the reel ceiling was reached. The
    video is then genuinely truncated, and saying so is better than a silent cut.
    """

    content_end_frame: int
    simulated_frames: int
    settled: bool


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


def _position(body) -> tuple:
    translation = body.matrix_world.translation
    return (translation.x, translation.y, translation.z)


def _distance(a: tuple, b: tuple) -> float:
    return ((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2) ** 0.5


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

    def simulate(self, budget_frames: int, fps: int, planned_frames: int, hold_frames: int = 0,
                 tracked_names: Optional[list] = None) -> SimulationOutcome:
        """Runs the simulation until the content is finished, and reports when that was.

        Four things have to be true first, and all four have bitten us:

        * The frame rate must already be the render frame rate, because the solver's timestep is one
          scene frame. Simulating at Blender's default 24 and playing back at 60 runs the reel two
          and a half times too fast.
        * The point cache must cover the frame range, or the motion stops partway through.
        * Something must actually advance the frames. A background render does not reliably step the
          solver, so every frame comes out with the object frozen at its starting transform.
        * The cache must be filled for every frame that will be rendered - including the hold - or
          the reel ends on frames the solver never produced.

        Stepping stops once the content has finished and the hold has been simulated. Nothing is
        forced, moved or removed to bring that moment forward; the simulation is simply watched until
        it is over.

        :param budget_frames:  the ceiling - the most frames the content is allowed to take
        :param planned_frames: what was asked for, used only when nothing ever moves
        :param hold_frames:    how much stillness to simulate after the content finishes
        :param tracked_names:  the bodies that count as required content
        """
        scene = self._scene
        scene.render.fps = fps
        scene.render.fps_base = 1.0
        scene.frame_start = 1
        scene.frame_end = budget_frames

        world = self._world()
        world.substeps_per_frame = SUBSTEPS_PER_FRAME
        world.solver_iterations = SOLVER_ITERATIONS
        world.point_cache.frame_start = 1
        # The cache covers the whole budget: it costs nothing to allow for frames that are never
        # simulated, and a cache that ends before the render does is the bug this guards against.
        world.point_cache.frame_end = budget_frames

        tracked = [body for body in (bpy.data.objects.get(name) for name in (tracked_names or []))
                   if body is not None]
        with measure("physics"):
            outcome = self._step(scene, budget_frames, planned_frames, hold_frames, tracked, fps)
            scene.frame_set(scene.frame_start)

        print("physics.content={0} simulated={1} settled={2}".format(
            outcome.content_end_frame, outcome.simulated_frames, str(outcome.settled).lower()))
        return outcome

    def _step(self, scene, budget_frames: int, planned_frames: int, hold_frames: int, tracked: list,
              fps: int) -> SimulationOutcome:
        """Walks the frames, watching for the moment nothing is moving any more."""
        rest_frames = max(1, int(round(REST_SECONDS * fps)))
        rest_per_frame = REST_SPEED / float(fps)

        previous = [_position(body) for body in tracked]
        heights = []
        moved = False
        still_since = None
        content_end = None
        frame = scene.frame_start

        while frame <= budget_frames:
            scene.frame_set(frame)
            current = [_position(body) for body in tracked]
            if tracked:
                heights.append(round(current[0][2], 3))

            travelled = max((_distance(a, b) for a, b in zip(previous, current)), default=0.0)
            previous = current

            if travelled > rest_per_frame:
                # Motion only counts as having finished once it has started: every body begins the
                # reel at rest, and the frame before a drop looks exactly like the frame after it
                # lands.
                moved = True
                still_since = None
            elif moved:
                still_since = frame if still_since is None else still_since
                if content_end is None and frame - still_since + 1 >= rest_frames:
                    # The content ends on the last frame that had motion in it; the stillness after
                    # that is the hold, not part of the content.
                    content_end = max(1, still_since - 1)

            if content_end is None and not moved and frame >= planned_frames:
                # A scene in which nothing ever moves has no content to wait for, so what was asked
                # for is the only sensible length.
                content_end = planned_frames

            if content_end is not None and frame >= content_end + hold_frames:
                break
            frame += 1

        simulated = min(frame, budget_frames)
        self._report(simulated, heights)
        if content_end is None:
            # Still moving at the ceiling: report the truth rather than pretending it finished.
            return SimulationOutcome(simulated, simulated, settled=False)
        return SimulationOutcome(content_end, simulated, settled=True)

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
