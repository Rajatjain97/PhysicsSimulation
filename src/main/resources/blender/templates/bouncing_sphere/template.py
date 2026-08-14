"""BouncingSphere - the first physics reel.

A glass sphere is dropped onto a studio floor and left to Blender's rigid body solver: it falls,
impacts, bounces with decreasing height as energy is lost, and settles. Nothing here is keyframed -
the motion is simulated, which is what makes this the template every future physics scene copies.

Parameters (all optional):
    durationSeconds  how long the reel runs, default 10
    dropHeight       height of the sphere's centre at frame 1, in metres, default 5
    physicsPreset    how the sphere behaves on impact: "Bouncy" (default) or "Heavy"
    material         shared material for the sphere, default "DefaultGlass"
    groundMaterial   shared material for the floor, default "DefaultMetal"

The camera framing is derived from dropHeight, so changing the drop does not push the sphere out of
frame. That is the whole parameterisation: a future story can generate a reel by naming a height, a
bounce and a material, and nothing in Blender or Java has to change.
"""

import math
from dataclasses import dataclass

import bpy

from engine.physics import MESH, SPHERE, RigidBodyPhysics
from engine.timeline import CAMERA_PRESET, SPAWN_OBJECT, START_PHYSICS, WAIT, Timeline
from engine.template_api import RenderSettings, Template, TemplateContext

DEFAULT_DURATION_SECONDS = 10.0
DEFAULT_DROP_HEIGHT = 5.0
DEFAULT_PHYSICS_PRESET = "Bouncy"
DEFAULT_MATERIAL = "DefaultGlass"
DEFAULT_GROUND_MATERIAL = "DefaultMetal"

SPHERE_NAME = "BouncingSphere"
GROUND_NAME = "Ground"
SPHERE_RADIUS = 0.6
FPS = 60
RENDER_SAMPLES = 32

CAMERA_DISTANCE = 9.0
SENSOR_HALF_WIDTH = 18.0
MIN_LENS = 24.0
MAX_LENS = 85.0
VERTICAL_MARGIN = 1.8


@dataclass(frozen=True)
class _ScenePlan:
    """The scene's intent, read once from the contract parameters."""

    duration_seconds: float
    drop_height: float
    physics_preset: str
    material: str
    ground_material: str


def _plan(context: TemplateContext) -> _ScenePlan:
    return _ScenePlan(
        duration_seconds=_positive_number(context, "durationSeconds", DEFAULT_DURATION_SECONDS),
        drop_height=_positive_number(context, "dropHeight", DEFAULT_DROP_HEIGHT),
        physics_preset=str(context.parameter("physicsPreset", DEFAULT_PHYSICS_PRESET)),
        material=str(context.parameter("material", DEFAULT_MATERIAL)),
        ground_material=str(context.parameter("groundMaterial", DEFAULT_GROUND_MATERIAL)))


class BouncingSphereTemplate(Template):

    name = "BouncingSphere"
    description = "A glass sphere dropped onto a studio floor: fall, impact, decaying bounces, rest."

    def configure_environment(self, context: TemplateContext) -> None:
        bpy.ops.wm.read_factory_settings(use_empty=True)
        scene = bpy.context.scene

        world = bpy.data.worlds.new("StudioWorld")
        world.use_nodes = True
        background = world.node_tree.nodes.get("Background")
        if background is not None:
            # Dark, but not black: the floor is a reflective surface and needs something to reflect,
            # otherwise the impact happens against an invisible ground.
            background.inputs[0].default_value = (0.05, 0.052, 0.062, 1.0)
            background.inputs[1].default_value = 1.0
        scene.world = world

        bpy.ops.mesh.primitive_plane_add(size=60.0, location=(0.0, 0.0, 0.0))
        ground = bpy.context.active_object
        ground.name = GROUND_NAME
        ground.data.materials.append(context.assets.materials.resolve(_plan(context).ground_material))

        RigidBodyPhysics(scene).add_static(ground, shape=MESH)

    def create_objects(self, context: TemplateContext) -> None:
        plan = _plan(context)

        bpy.ops.mesh.primitive_uv_sphere_add(
            radius=SPHERE_RADIUS, location=(0.0, 0.0, plan.drop_height), segments=64, ring_count=32)
        sphere = bpy.context.active_object
        sphere.name = SPHERE_NAME
        bpy.ops.object.shade_smooth()
        sphere.data.materials.append(context.assets.materials.resolve(plan.material))

        RigidBodyPhysics(bpy.context.scene).add_dynamic(sphere, shape=SPHERE,
                                                        preset_name=plan.physics_preset)

    def configure_camera(self, context: TemplateContext) -> None:
        # Frame the whole fall: the sphere's starting point, the floor, and a little air around both.
        coverage = _plan(context).drop_height + SPHERE_RADIUS + VERTICAL_MARGIN
        lens = min(MAX_LENS, max(MIN_LENS, (2.0 * SENSOR_HALF_WIDTH * CAMERA_DISTANCE) / coverage))

        scene = bpy.context.scene
        camera_data = bpy.data.cameras.new("StudioCamera")
        camera_data.lens = lens
        camera = bpy.data.objects.new("StudioCamera", camera_data)
        camera.location = (0.0, -CAMERA_DISTANCE, coverage / 2.0 - 0.6)
        camera.rotation_euler = (math.radians(87.0), 0.0, 0.0)
        scene.collection.objects.link(camera)
        scene.camera = camera

    def configure_lighting(self, context: TemplateContext) -> None:
        _add_area_light("KeyLight", energy=2400.0, size=8.0,
                        location=(3.4, -4.2, 7.0), rotation=(math.radians(40.0), 0.0, math.radians(38.0)))
        _add_area_light("FillLight", energy=600.0, size=10.0,
                        location=(-4.4, -3.0, 3.0), rotation=(math.radians(70.0), 0.0, math.radians(-52.0)))
        _add_area_light("RimLight", energy=1300.0, size=5.0,
                        location=(0.0, 4.2, 4.6), rotation=(math.radians(122.0), 0.0, 0.0))
        # Straight down onto the impact point, so the contact with the floor is unmistakable.
        _add_area_light("ContactLight", energy=900.0, size=4.0,
                        location=(0.0, -1.2, 6.0), rotation=(0.0, 0.0, 0.0))

    def prepare_for_rendering(self, context: TemplateContext) -> None:
        scene = bpy.context.scene
        scene.render.film_transparent = False

        # The reel's duration is the single source of truth for the simulation length.
        settings = self.render_settings(context)
        RigidBodyPhysics(scene).simulate(settings.frames, settings.fps, tracked_name=SPHERE_NAME)

    def render_settings(self, context: TemplateContext) -> RenderSettings:
        # EEVEE, not Cycles: a ten second reel is 600 frames, and this has to finish on a laptop.
        return RenderSettings(engine="EEVEE", samples=RENDER_SAMPLES, fps=FPS,
                              duration_seconds=_plan(context).duration_seconds)

    def timeline(self, context: TemplateContext) -> Timeline:
        """What this reel is, as intent: two objects, a framing, a simulation, and time to watch it.

        Nothing reads this yet - build() still does the work - but it is the same scene described
        without a single Blender call, which is what a later story will execute instead.
        """
        plan = _plan(context)
        timeline = Timeline()
        timeline.add(SPAWN_OBJECT, at=0.0, name=GROUND_NAME, shape="plane",
                     material=plan.ground_material, physics="static")
        timeline.add(SPAWN_OBJECT, at=0.0, name=SPHERE_NAME, shape="sphere",
                     radius=SPHERE_RADIUS, height=plan.drop_height, material=plan.material)
        timeline.add(CAMERA_PRESET, at=0.0, framing="portrait-drop", covers=plan.drop_height)
        timeline.add(START_PHYSICS, at=0.0, preset=plan.physics_preset, target=SPHERE_NAME)
        timeline.add(WAIT, at=0.0, duration=plan.duration_seconds)
        return timeline



def _add_area_light(name: str, energy: float, size: float, location: tuple, rotation: tuple) -> None:
    light_data = bpy.data.lights.new(name, type="AREA")
    light_data.energy = energy
    light_data.size = size
    if hasattr(light_data, "use_shadow"):
        light_data.use_shadow = True
    light = bpy.data.objects.new(name, light_data)
    light.location = location
    light.rotation_euler = rotation
    bpy.context.scene.collection.objects.link(light)


def _number(context: TemplateContext, name: str, default: float) -> float:
    """Parameters arrive from JSON, so a number may be written as a string. Fail clearly if it is not."""
    value = context.parameter(name, default)
    try:
        return float(value)
    except (TypeError, ValueError):
        raise ValueError("Parameter '{0}' must be a number but was {1!r}".format(name, value))


def _positive_number(context: TemplateContext, name: str, default: float) -> float:
    value = _number(context, name, default)
    if value <= 0.0:
        raise ValueError("Parameter '{0}' must be greater than zero but was {1}".format(name, value))
    return value


TEMPLATE = BouncingSphereTemplate()
