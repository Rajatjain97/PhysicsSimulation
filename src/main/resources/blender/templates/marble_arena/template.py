"""MarbleArena - the flagship reel.

Twenty-five glass marbles are dropped into a circular glass arena with one gap in the wall. They
collide, scatter, and some find the exit. Nothing about the outcome is scripted: the marbles are
placed, the solver runs, and whatever survives in the arena survives.

Parameters (all optional):
    durationSeconds  how long the reel runs, default 20 (the format wants 15-30)
    marbleCount      how many marbles, default 25
    seed             placement seed; the same seed always gives the same reel, default 20260814
    physicsPreset    how the marbles behave: "Bouncy" (default) or "Heavy"
    cameraPreset     "TopDown" (default), "Orbit", "FollowObject", "SlowZoom" or "Static"
    material         shared material for the marbles, default "DefaultGlass"
    arenaMaterial    shared material for the walls, default "DefaultGlass"
    floorMaterial    shared material for the floor, default "DefaultMetal"

The arena is described, never built here: every wall segment, marble and camera move is a timeline
event, so a future story can rotate the arena, open the gate or add a second floor by emitting
different events rather than editing this file.

Placement is a seeded sunflower spiral - even spacing with no overlaps, and identical every run.
Marbles start at staggered heights so they arrive in a cascade rather than all at once, which is what
makes the opening seconds worth watching.
"""

import math
import random
from dataclasses import dataclass

from engine.template_api import RenderSettings, Template, TemplateContext
from engine.timeline import CAMERA_PRESET, SPAWN_OBJECT, START_PHYSICS, WAIT, Timeline

DEFAULT_DURATION_SECONDS = 20.0
DEFAULT_MARBLE_COUNT = 25
DEFAULT_SEED = 20260814
DEFAULT_PHYSICS_PRESET = "Bouncy"
DEFAULT_CAMERA_PRESET = "TopDown"
DEFAULT_MARBLE_MATERIAL = "DefaultGlass"
DEFAULT_ARENA_MATERIAL = "DefaultGlass"
DEFAULT_FLOOR_MATERIAL = "DefaultMetal"

FPS = 60
RENDER_SAMPLES = 32

# Arena geometry. Sized so the whole thing sits comfortably inside a 9:16 frame from overhead.
ARENA_RADIUS = 5.0
WALL_SEGMENTS = 28
EXIT_SEGMENTS = 2
WALL_HEIGHT = 1.2
WALL_THICKNESS = 0.16
FLOOR_SIZE = 40.0
FRAME_MARGIN = 2.4

MARBLE_RADIUS = 0.35
DROP_BASE_HEIGHT = 3.0
DROP_STAGGER = 0.55
DROP_LEVELS = 5
PLACEMENT_SPREAD = 0.78
PLACEMENT_JITTER = 0.12

GOLDEN_ANGLE = math.pi * (3.0 - math.sqrt(5.0))

# A small palette rather than free colour: a handful of tints reads as a set of teams, and each one
# becomes a single shared material variant instead of twenty-five one-off materials.
PALETTE = (
    ("Amber", (1.0, 0.62, 0.16)),
    ("Rose", (0.95, 0.25, 0.42)),
    ("Mint", (0.28, 0.92, 0.62)),
    ("Azure", (0.24, 0.55, 0.98)),
    ("Violet", (0.62, 0.35, 0.96)),
    ("Lime", (0.72, 0.94, 0.24)),
)

FLOOR_NAME = "ArenaFloor"
WALL_NAME = "ArenaWall"
MARBLE_NAME = "Marble"


@dataclass(frozen=True)
class _ArenaPlan:
    """The reel's intent, read once from the contract parameters."""

    duration_seconds: float
    marble_count: int
    seed: int
    physics_preset: str
    camera_preset: str
    marble_material: str
    arena_material: str
    floor_material: str


def _plan(context: TemplateContext) -> _ArenaPlan:
    return _ArenaPlan(
        duration_seconds=_positive_number(context, "durationSeconds", DEFAULT_DURATION_SECONDS),
        marble_count=int(_positive_number(context, "marbleCount", DEFAULT_MARBLE_COUNT)),
        seed=int(_number(context, "seed", DEFAULT_SEED)),
        physics_preset=str(context.parameter("physicsPreset", DEFAULT_PHYSICS_PRESET)),
        camera_preset=str(context.parameter("cameraPreset", DEFAULT_CAMERA_PRESET)),
        marble_material=str(context.parameter("material", DEFAULT_MARBLE_MATERIAL)),
        arena_material=str(context.parameter("arenaMaterial", DEFAULT_ARENA_MATERIAL)),
        floor_material=str(context.parameter("floorMaterial", DEFAULT_FLOOR_MATERIAL)))


class MarbleArenaTemplate(Template):

    name = "MarbleArena"
    description = "Glass marbles dropped into a circular arena with one exit; physics decides who stays."

    def configure_environment(self, context: TemplateContext) -> None:
        import bpy

        bpy.ops.wm.read_factory_settings(use_empty=True)
        scene = bpy.context.scene
        scene.render.film_transparent = False

        world = bpy.data.worlds.new("ArenaWorld")
        world.use_nodes = True
        background = world.node_tree.nodes.get("Background")
        if background is not None:
            background.inputs[0].default_value = (0.035, 0.037, 0.045, 1.0)
            background.inputs[1].default_value = 1.0
        scene.world = world

    def configure_lighting(self, context: TemplateContext) -> None:
        import bpy

        # An overhead softbox for the arena, plus two rakes so the glass has edges to catch.
        _add_area_light(bpy, "KeyLight", energy=4200.0, size=14.0,
                        location=(0.0, 0.0, 11.0), rotation=(0.0, 0.0, 0.0))
        _add_area_light(bpy, "RakeLeft", energy=1400.0, size=8.0,
                        location=(-7.5, -6.0, 6.0), rotation=(math.radians(52.0), 0.0, math.radians(-42.0)))
        _add_area_light(bpy, "RakeRight", energy=1400.0, size=8.0,
                        location=(7.5, 6.0, 6.0), rotation=(math.radians(52.0), 0.0, math.radians(138.0)))

    def render_settings(self, context: TemplateContext) -> RenderSettings:
        return RenderSettings(engine="EEVEE", samples=RENDER_SAMPLES, fps=FPS,
                              duration_seconds=_plan(context).duration_seconds)

    def timeline(self, context: TemplateContext) -> Timeline:
        plan = _plan(context)
        timeline = Timeline()

        timeline.add(SPAWN_OBJECT, at=0.0, name=FLOOR_NAME, shape="plane", size=FLOOR_SIZE,
                     material=plan.floor_material, physics="static")
        self._add_arena(timeline, plan)
        marbles = self._add_marbles(timeline, plan)

        timeline.add(CAMERA_PRESET, at=0.0, preset=plan.camera_preset,
                     covers=2.0 * ARENA_RADIUS + FRAME_MARGIN, target=marbles[0])
        timeline.add(START_PHYSICS, at=0.0, preset=plan.physics_preset, targets=marbles)
        timeline.add(WAIT, at=0.0, duration=plan.duration_seconds)
        return timeline

    @staticmethod
    def _add_arena(timeline: Timeline, plan: _ArenaPlan) -> None:
        """A ring of wall segments with a gap left in it. The gap is the only way out."""
        # Overlap each segment slightly so the ring has no seams for a marble to squeeze through.
        half_width = (math.pi * ARENA_RADIUS / WALL_SEGMENTS) * 1.06

        for index in range(WALL_SEGMENTS):
            if index < EXIT_SEGMENTS:
                continue
            angle = 2.0 * math.pi * index / WALL_SEGMENTS
            timeline.add(SPAWN_OBJECT, at=0.0, name="{0}{1:02d}".format(WALL_NAME, index), shape="box",
                         size=2.0,
                         location=[ARENA_RADIUS * math.cos(angle), ARENA_RADIUS * math.sin(angle),
                                   WALL_HEIGHT / 2.0],
                         rotation=[0.0, 0.0, math.degrees(angle) + 90.0],
                         scale=[half_width / 2.0, WALL_THICKNESS / 2.0, WALL_HEIGHT / 2.0],
                         material=plan.arena_material, physics="static")

    @staticmethod
    def _add_marbles(timeline: Timeline, plan: _ArenaPlan) -> list:
        """Places the marbles on a seeded sunflower spiral and returns their names."""
        placement = random.Random(plan.seed)
        names = []
        for index in range(plan.marble_count):
            radius = ARENA_RADIUS * PLACEMENT_SPREAD * math.sqrt((index + 0.5) / plan.marble_count)
            angle = index * GOLDEN_ANGLE
            colour_name, tint = PALETTE[index % len(PALETTE)]
            name = "{0}{1:02d}".format(MARBLE_NAME, index)
            names.append(name)

            timeline.add(SPAWN_OBJECT, at=0.0, name=name, shape="sphere", radius=MARBLE_RADIUS,
                         location=[radius * math.cos(angle) + placement.uniform(-PLACEMENT_JITTER, PLACEMENT_JITTER),
                                   radius * math.sin(angle) + placement.uniform(-PLACEMENT_JITTER, PLACEMENT_JITTER),
                                   DROP_BASE_HEIGHT + (index % DROP_LEVELS) * DROP_STAGGER],
                         material=plan.marble_material, tint=list(tint), tintName=colour_name)
        return names


def _add_area_light(bpy, name: str, energy: float, size: float, location: tuple, rotation: tuple) -> None:
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


TEMPLATE = MarbleArenaTemplate()
