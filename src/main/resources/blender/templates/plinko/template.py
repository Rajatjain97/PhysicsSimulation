"""Plinko - balls fall through a staggered peg field into coloured buckets.

The classic board, built for a phone screen: a tall, narrow field of pegs, a dozen balls released
from the top, and six buckets at the bottom. Where each ball ends up is decided entirely by the
solver. Nothing is keyframed, nothing is nudged, and the template never computes an outcome - which
is what makes the reel worth watching to the end.

Parameters (all optional):
    durationSeconds  target length, default 12 (the format wants 8-15); the reel runs until the
                     last ball has stopped moving, so this is what the board is planned around
                     rather than a point at which the video is cut
    ballCount        how many balls fall, default 12
    rows             rows of pegs, default 8
    physicsPreset    "Heavy" (default) keeps balls tumbling rather than pinballing; or "Bouncy"
    cameraPreset     "Static" (default) frames the whole board head-on; any preset is accepted
    material         shared material for the balls, default "DefaultGlass"
    boardMaterial    shared material for the board, pegs and walls, default "DefaultMetal"
    hookText         opening caption, default "Where will they land?"
    resultText       closing caption, default "RESULT"

The board lives in the XZ plane so gravity pulls balls down the screen. Two thin walls hold them in
that plane; the front one is spawned invisible, because a wall between the camera and the pegs would
hide the whole reel.

Geometry is sized so the entire board fits the portrait frame at the Static camera's working
distance: at 9m with a 24mm minimum lens, roughly 13.3m of height is visible, so the board is 11.5m
tall and 6.5m wide with room to spare on both axes.

Every random choice - where a ball starts and what colour it is - comes from the render seed, so the
same seed rebuilds the same board and a batch of ten produces ten genuinely different reels.
"""

import math
from dataclasses import dataclass

from engine.studio import create_area_light, set_world_background
from engine.template_api import RenderSettings, Template, TemplateContext, positive_number
from engine.timeline import CAMERA_PRESET, SHOW_TEXT, SPAWN_OBJECT, START_PHYSICS, WAIT, Timeline

DEFAULT_DURATION_SECONDS = 12.0
DEFAULT_BALL_COUNT = 12
DEFAULT_ROWS = 8
DEFAULT_PHYSICS_PRESET = "Heavy"
DEFAULT_CAMERA_PRESET = "Static"
DEFAULT_BALL_MATERIAL = "DefaultGlass"
DEFAULT_BOARD_MATERIAL = "DefaultMetal"
DEFAULT_HOOK_TEXT = "Where will they land?"
DEFAULT_RESULT_TEXT = "RESULT"

FPS = 60
RENDER_SAMPLES = 32

MIN_ROWS = 3
MAX_ROWS = 14
MAX_BALLS = 60

# Board. Chosen to fill a 9:16 frame at the Static camera's distance without touching the edges.
BOARD_WIDTH = 6.5
BOARD_HEIGHT = 11.5
BOARD_DEPTH = 0.35
PANEL_THICKNESS = 0.12
WALL_THICKNESS = 0.18

# Peg field, inset from the walls and stopping above the buckets.
PEG_TOP = 8.6
PEG_BOTTOM = 3.2
PEG_RADIUS = 0.15
PEGS_PER_ROW = 6

# Balls. The radius is what makes the board playable: two pegs are 0.93 apart, so a 0.22 ball passes
# between them but cannot slip through a diagonal gap.
BALL_RADIUS = 0.22
SPAWN_HEIGHT = 9.6
SPAWN_SPREAD = 1.5
SPAWN_COLUMNS = 4
SPAWN_ROW_HEIGHT = 0.62
SPAWN_JITTER = 0.16

BUCKET_COUNT = 6
BUCKET_HEIGHT = 1.9
BUCKET_FLOOR_THICKNESS = 0.16
DIVIDER_THICKNESS = 0.14

HOOK_SECONDS = 2.5
RESULT_SECONDS = 2.5

# Restrained: the board is neutral metal, and only the balls and bucket floors carry colour.
BALL_PALETTE = (
    ("Amber", (1.0, 0.62, 0.16)),
    ("Rose", (0.95, 0.25, 0.42)),
    ("Mint", (0.28, 0.92, 0.62)),
    ("Azure", (0.24, 0.55, 0.98)),
    ("Violet", (0.62, 0.35, 0.96)),
)
BUCKET_PALETTE = (
    ("BucketCool", (0.16, 0.34, 0.62)),
    ("BucketWarm", (0.62, 0.30, 0.22)),
)

BALL_NAME = "Ball"
PEG_NAME = "Peg"
BUCKET_FLOOR_NAME = "BucketFloor"
DIVIDER_NAME = "Divider"


@dataclass(frozen=True)
class _BoardPlan:
    """The reel's intent, read once from the contract parameters."""

    duration_seconds: float
    ball_count: int
    rows: int
    physics_preset: str
    camera_preset: str
    ball_material: str
    board_material: str
    hook_text: str
    result_text: str


def _plan(context: TemplateContext) -> _BoardPlan:
    rows = int(positive_number(context, "rows", DEFAULT_ROWS))
    if not MIN_ROWS <= rows <= MAX_ROWS:
        raise ValueError("Parameter 'rows' must be between {0} and {1} but was {2}".format(
            MIN_ROWS, MAX_ROWS, rows))
    ball_count = int(positive_number(context, "ballCount", DEFAULT_BALL_COUNT))
    if ball_count > MAX_BALLS:
        raise ValueError("Parameter 'ballCount' must not exceed {0} but was {1}".format(MAX_BALLS, ball_count))

    return _BoardPlan(
        duration_seconds=positive_number(context, "durationSeconds", DEFAULT_DURATION_SECONDS),
        ball_count=ball_count,
        rows=rows,
        physics_preset=str(context.parameter("physicsPreset", DEFAULT_PHYSICS_PRESET)),
        camera_preset=str(context.parameter("cameraPreset", DEFAULT_CAMERA_PRESET)),
        ball_material=str(context.parameter("material", DEFAULT_BALL_MATERIAL)),
        board_material=str(context.parameter("boardMaterial", DEFAULT_BOARD_MATERIAL)),
        hook_text=str(context.parameter("hookText", DEFAULT_HOOK_TEXT)),
        result_text=str(context.parameter("resultText", DEFAULT_RESULT_TEXT)))


class PlinkoTemplate(Template):

    name = "Plinko"
    description = "Balls fall through a staggered peg field into coloured buckets; physics picks the winner."

    def configure_environment(self, context: TemplateContext) -> None:
        import bpy

        bpy.ops.wm.read_factory_settings(use_empty=True)
        scene = bpy.context.scene
        scene.render.film_transparent = False

        set_world_background(scene, "PlinkoWorld", (0.02, 0.022, 0.028, 1.0))

    def configure_lighting(self, context: TemplateContext) -> None:
        import bpy

        scene = bpy.context.scene
        # Frontal key so the balls read clearly against the board, plus two rakes for the peg edges.
        create_area_light(scene, "KeyLight", energy=3200.0, size=12.0,
                          location=(0.0, -7.0, 8.0), rotation=(math.radians(72.0), 0.0, 0.0))
        create_area_light(scene, "RakeLeft", energy=900.0, size=6.0,
                          location=(-6.0, -4.5, 9.5), rotation=(math.radians(55.0), 0.0, math.radians(-40.0)))
        create_area_light(scene, "RakeRight", energy=900.0, size=6.0,
                          location=(6.0, -4.5, 9.5), rotation=(math.radians(55.0), 0.0, math.radians(40.0)))

    def render_settings(self, context: TemplateContext) -> RenderSettings:
        return RenderSettings(engine="EEVEE", samples=RENDER_SAMPLES, fps=FPS,
                              duration_seconds=_plan(context).duration_seconds)

    def timeline(self, context: TemplateContext) -> Timeline:
        plan = _plan(context)
        timeline = Timeline()

        self._add_board(timeline, plan)
        self._add_pegs(timeline, plan)
        self._add_buckets(timeline, plan)
        balls = self._add_balls(timeline, plan, context)

        # Physics first: how long the reel is depends on when the last ball comes to rest, and the
        # camera and the closing caption are both scheduled against that length.
        timeline.add(START_PHYSICS, at=0.0, preset=plan.physics_preset, targets=balls)
        timeline.add(CAMERA_PRESET, at=0.0, preset=plan.camera_preset,
                     covers=BOARD_HEIGHT, target=balls[0])

        # Captions sit at the top: the action moves downwards, so the drop zone is the only part of
        # the frame that is empty when each one is on screen.
        timeline.add(SHOW_TEXT, at=0.0, duration=HOOK_SECONDS, style="Hook", position="Top",
                     name="HookCaption", text=plan.hook_text)
        timeline.add(SHOW_TEXT, at=0.0, duration=RESULT_SECONDS, anchor="end",
                     style="Winner", position="Top",
                     name="ResultCaption", text=plan.result_text)

        timeline.add(WAIT, at=0.0, duration=plan.duration_seconds)
        return timeline

    @staticmethod
    def _add_board(timeline: Timeline, plan: _BoardPlan) -> None:
        """Backing, side walls, and the two panels that keep the balls in the board's plane."""
        centre = BOARD_HEIGHT / 2.0
        material = plan.board_material

        timeline.add(SPAWN_OBJECT, at=0.0, name="BoardBacking", shape="box", size=2.0,
                     location=[0.0, BOARD_DEPTH, centre],
                     scale=[BOARD_WIDTH / 2.0, PANEL_THICKNESS / 2.0, BOARD_HEIGHT / 2.0],
                     material=material, physics="static")

        # The camera looks along +Y, so this panel would be in front of everything. It exists only to
        # stop balls drifting out of the board, and is spawned invisible.
        timeline.add(SPAWN_OBJECT, at=0.0, name="BoardFront", shape="box", size=2.0,
                     location=[0.0, -BOARD_DEPTH, centre],
                     scale=[BOARD_WIDTH / 2.0, PANEL_THICKNESS / 2.0, BOARD_HEIGHT / 2.0],
                     physics="static", visible=False)

        for side, x in (("Left", -BOARD_WIDTH / 2.0), ("Right", BOARD_WIDTH / 2.0)):
            timeline.add(SPAWN_OBJECT, at=0.0, name="Wall" + side, shape="box", size=2.0,
                         location=[x, 0.0, centre],
                         scale=[WALL_THICKNESS / 2.0, BOARD_DEPTH, BOARD_HEIGHT / 2.0],
                         material=material, physics="static")

    @staticmethod
    def _add_pegs(timeline: Timeline, plan: _BoardPlan) -> None:
        """Staggered rows: every other row is offset by half a spacing, which is what deflects balls."""
        spacing = BOARD_WIDTH / (PEGS_PER_ROW + 1)
        vertical = (PEG_TOP - PEG_BOTTOM) / max(plan.rows - 1, 1)

        for row in range(plan.rows):
            z = PEG_TOP - row * vertical
            staggered = row % 2 == 1
            count = PEGS_PER_ROW - 1 if staggered else PEGS_PER_ROW
            offset = 1.5 if staggered else 1.0

            for index in range(count):
                x = -BOARD_WIDTH / 2.0 + spacing * (index + offset)
                timeline.add(SPAWN_OBJECT, at=0.0, name="{0}{1:02d}{2:02d}".format(PEG_NAME, row, index),
                             shape="sphere", radius=PEG_RADIUS, location=[x, 0.0, z],
                             material=plan.board_material, physics="static")

    @staticmethod
    def _add_buckets(timeline: Timeline, plan: _BoardPlan) -> None:
        """Coloured floors with dividers between them, so where a ball landed is obvious on screen."""
        width = BOARD_WIDTH / BUCKET_COUNT

        for index in range(BUCKET_COUNT):
            colour_name, tint = BUCKET_PALETTE[index % len(BUCKET_PALETTE)]
            x = -BOARD_WIDTH / 2.0 + width * (index + 0.5)
            timeline.add(SPAWN_OBJECT, at=0.0, name="{0}{1:02d}".format(BUCKET_FLOOR_NAME, index),
                         shape="box", size=2.0,
                         location=[x, 0.0, BUCKET_FLOOR_THICKNESS / 2.0],
                         scale=[width / 2.0, BOARD_DEPTH, BUCKET_FLOOR_THICKNESS / 2.0],
                         material=plan.board_material, tint=list(tint), tintName=colour_name,
                         physics="static")

        for index in range(BUCKET_COUNT + 1):
            x = -BOARD_WIDTH / 2.0 + width * index
            timeline.add(SPAWN_OBJECT, at=0.0, name="{0}{1:02d}".format(DIVIDER_NAME, index),
                         shape="box", size=2.0,
                         location=[x, 0.0, BUCKET_HEIGHT / 2.0],
                         scale=[DIVIDER_THICKNESS / 2.0, BOARD_DEPTH, BUCKET_HEIGHT / 2.0],
                         material=plan.board_material, physics="static")

    @staticmethod
    def _add_balls(timeline: Timeline, plan: _BoardPlan, context: TemplateContext) -> list:
        """Drops the balls from a spread above the pegs, and returns their names.

        The horizontal jitter matters physically as well as visually: a ball released exactly above a
        peg is a coin balanced on its edge, and the solver has to break the tie with floating point
        noise. Jittering each one off its column makes the first bounce meaningful instead of
        arbitrary - and it is drawn from the render seed, so it is meaningful *and* reproducible.
        """
        spawn = context.random.stream("ball.spawn")
        colours = context.random.stream("ball.colour")

        # A grid rather than a column: rows are taller than a ball so nothing starts interpenetrating,
        # and the whole cluster stays inside the frame instead of trailing off the top.
        columns = min(SPAWN_COLUMNS, plan.ball_count)
        column_spacing = (2.0 * SPAWN_SPREAD) / max(columns - 1, 1) if columns > 1 else 0.0

        names = []
        for index in range(plan.ball_count):
            name = "{0}{1:02d}".format(BALL_NAME, index)
            colour_name, tint = colours.choice(BALL_PALETTE)
            names.append(name)

            column = index % columns
            row = index // columns
            x = (-SPAWN_SPREAD + column * column_spacing) if columns > 1 else 0.0

            timeline.add(SPAWN_OBJECT, at=0.0, name=name, shape="sphere", radius=BALL_RADIUS,
                         location=[x + spawn.uniform(-SPAWN_JITTER, SPAWN_JITTER), 0.0,
                                   SPAWN_HEIGHT + row * SPAWN_ROW_HEIGHT],
                         material=plan.ball_material, tint=list(tint), tintName=colour_name)
        return names





TEMPLATE = PlinkoTemplate()
