"""BouncingSphere - the first physics reel.

A glass sphere is dropped onto a studio floor and left to Blender's rigid body solver: it falls,
impacts, bounces with decreasing height as energy is lost, and settles. Nothing here is keyframed -
the motion is simulated, which is what makes this the template every future physics scene copies.

Parameters (all optional):
    durationSeconds  target length, default 10; the reel runs until the sphere has settled, so
                     this is a target rather than a cut
    dropHeight       height of the sphere's centre at frame 1, in metres, default 5
    physicsPreset    how the sphere behaves on impact: "Bouncy" (default) or "Heavy"
    cameraPreset     how the reel is shot: "Static" (default), "FollowObject", "Orbit",
                     "SlowZoom" or "TopDown"
    material         shared material for the sphere, default "DefaultGlass"
    groundMaterial   shared material for the floor, default "DefaultMetal"

The template describes the reel and nothing else: what to spawn, how to frame it, when physics
starts, how long it runs. The scene director carries that out. What is left here is the environment
and the lighting, which have no event kinds yet.
"""

import math
from dataclasses import dataclass

import bpy

from engine.studio import create_area_light, set_world_background
from engine.timeline import CAMERA_PRESET, SPAWN_OBJECT, START_PHYSICS, WAIT, Timeline
from engine.template_api import RenderSettings, Template, TemplateContext, positive_number

DEFAULT_DURATION_SECONDS = 10.0
DEFAULT_DROP_HEIGHT = 5.0
DEFAULT_PHYSICS_PRESET = "Bouncy"
DEFAULT_CAMERA_PRESET = "Static"
DEFAULT_MATERIAL = "DefaultGlass"
DEFAULT_GROUND_MATERIAL = "DefaultMetal"

SPHERE_NAME = "BouncingSphere"
GROUND_NAME = "Ground"
SPHERE_RADIUS = 0.6
GROUND_SIZE = 60.0
FPS = 60
RENDER_SAMPLES = 32



@dataclass(frozen=True)
class _ScenePlan:
    """The scene's intent, read once from the contract parameters."""

    duration_seconds: float
    drop_height: float
    physics_preset: str
    camera_preset: str
    material: str
    ground_material: str


def _plan(context: TemplateContext) -> _ScenePlan:
    return _ScenePlan(
        duration_seconds=positive_number(context, "durationSeconds", DEFAULT_DURATION_SECONDS),
        drop_height=positive_number(context, "dropHeight", DEFAULT_DROP_HEIGHT),
        physics_preset=str(context.parameter("physicsPreset", DEFAULT_PHYSICS_PRESET)),
        camera_preset=str(context.parameter("cameraPreset", DEFAULT_CAMERA_PRESET)),
        material=str(context.parameter("material", DEFAULT_MATERIAL)),
        ground_material=str(context.parameter("groundMaterial", DEFAULT_GROUND_MATERIAL)))


class BouncingSphereTemplate(Template):

    name = "BouncingSphere"
    description = "A glass sphere dropped onto a studio floor: fall, impact, decaying bounces, rest."

    def configure_environment(self, context: TemplateContext) -> None:
        bpy.ops.wm.read_factory_settings(use_empty=True)
        scene = bpy.context.scene

        # Dark, but not black: the floor is a reflective surface and needs something to reflect,
        # otherwise the impact happens against an invisible ground.
        set_world_background(scene, "StudioWorld", (0.05, 0.052, 0.062, 1.0))

        scene.render.film_transparent = False

    def configure_lighting(self, context: TemplateContext) -> None:
        scene = bpy.context.scene
        create_area_light(scene, "KeyLight", energy=2400.0, size=8.0,
                          location=(3.4, -4.2, 7.0), rotation=(math.radians(40.0), 0.0, math.radians(38.0)))
        create_area_light(scene, "FillLight", energy=600.0, size=10.0,
                          location=(-4.4, -3.0, 3.0), rotation=(math.radians(70.0), 0.0, math.radians(-52.0)))
        create_area_light(scene, "RimLight", energy=1300.0, size=5.0,
                          location=(0.0, 4.2, 4.6), rotation=(math.radians(122.0), 0.0, 0.0))
        # Straight down onto the impact point, so the contact with the floor is unmistakable.
        create_area_light(scene, "ContactLight", energy=900.0, size=4.0,
                          location=(0.0, -1.2, 6.0), rotation=(0.0, 0.0, 0.0))

    def render_settings(self, context: TemplateContext) -> RenderSettings:
        # EEVEE, not Cycles: a ten second reel is 600 frames, and this has to finish on a laptop.
        return RenderSettings(engine="EEVEE", samples=RENDER_SAMPLES, fps=FPS,
                              duration_seconds=_plan(context).duration_seconds)

    def timeline(self, context: TemplateContext) -> Timeline:
        """What this reel is, as intent: two objects, a framing, a simulation, and time to watch it.

        The scene director carries this out; the whole scene is described here without a single
        Blender call.
        """
        plan = _plan(context)
        timeline = Timeline()
        timeline.add(SPAWN_OBJECT, at=0.0, name=GROUND_NAME, shape="plane", size=GROUND_SIZE,
                     material=plan.ground_material, physics="static")
        timeline.add(SPAWN_OBJECT, at=0.0, name=SPHERE_NAME, shape="sphere",
                     radius=SPHERE_RADIUS, height=plan.drop_height, material=plan.material)
        # The camera is described, not configured: which shot, what has to stay in frame, and what
        # to look at if the shot tracks something.
        # Physics first: the sphere decides how long the reel is, and the camera is framed against
        # that length.
        timeline.add(START_PHYSICS, at=0.0, preset=plan.physics_preset, target=SPHERE_NAME)
        timeline.add(CAMERA_PRESET, at=0.0, preset=plan.camera_preset,
                     covers=plan.drop_height + SPHERE_RADIUS, target=SPHERE_NAME)
        timeline.add(WAIT, at=0.0, duration=plan.duration_seconds)
        return timeline






TEMPLATE = BouncingSphereTemplate()
