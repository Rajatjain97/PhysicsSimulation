"""Physics Reel Studio - the default scene template.

A template owns the parts of a scene that every render shares: camera, lighting, world and render
settings. It owns no content - the objects come from the scene contract.

Kept as Python rather than a binary .blend on purpose: it is reviewable in a pull request and diffs
mean something. The loader prefers 'default.blend' over 'default.py' if both exist, so an artist can
save a real .blend here later without any Java or script change.

Vertical 1080x1920 because everything this project renders is a Reel. Cycles on the CPU with a low
sample count because it renders headlessly on any machine, including CI; quality tuning belongs to a
later story.
"""

import math

import bpy

RESOLUTION_X = 1080
RESOLUTION_Y = 1920
SAMPLES = 32


def build() -> None:
    """Entry point called by render_scene.py."""
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    _build_world(scene)
    _add_camera(scene)
    _add_lighting(scene)
    _configure_render(scene)


def _build_world(scene) -> None:
    world = bpy.data.worlds.new("PhysicsReelWorld")
    world.use_nodes = True
    background = world.node_tree.nodes.get("Background")
    if background is not None:
        background.inputs[0].default_value = (0.02, 0.02, 0.03, 1.0)
        background.inputs[1].default_value = 1.0
    scene.world = world


def _add_camera(scene) -> None:
    camera_data = bpy.data.cameras.new("Camera")
    camera_data.lens = 50.0
    camera = bpy.data.objects.new("Camera", camera_data)
    camera.location = (0.0, -6.0, 1.4)
    camera.rotation_euler = (math.radians(84.0), 0.0, 0.0)
    scene.collection.objects.link(camera)
    scene.camera = camera


def _add_lighting(scene) -> None:
    key_data = bpy.data.lights.new("KeyLight", type="AREA")
    key_data.energy = 900.0
    key_data.size = 6.0
    key = bpy.data.objects.new("KeyLight", key_data)
    key.location = (4.0, -5.0, 6.0)
    key.rotation_euler = (math.radians(45.0), 0.0, math.radians(38.0))
    scene.collection.objects.link(key)

    fill_data = bpy.data.lights.new("FillLight", type="AREA")
    fill_data.energy = 250.0
    fill_data.size = 8.0
    fill = bpy.data.objects.new("FillLight", fill_data)
    fill.location = (-5.0, -4.0, 2.0)
    fill.rotation_euler = (math.radians(70.0), 0.0, math.radians(-55.0))
    scene.collection.objects.link(fill)


def _configure_render(scene) -> None:
    render = scene.render
    render.resolution_x = RESOLUTION_X
    render.resolution_y = RESOLUTION_Y
    render.resolution_percentage = 100
    render.fps = 30
    render.film_transparent = False
    render.image_settings.file_format = "PNG"
    render.image_settings.color_mode = "RGBA"
    render.engine = "CYCLES"

    cycles = getattr(scene, "cycles", None)
    if cycles is not None:
        cycles.device = "CPU"
        cycles.samples = SAMPLES
        cycles.use_denoising = True
