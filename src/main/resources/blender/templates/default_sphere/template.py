"""DefaultSphere - a single glass sphere in a clean studio.

The reference template: the simplest scene that still looks like premium social content, and the file
to copy when writing a new one. Everything visual is here - world, backdrop, material, camera,
lights - because that is exactly what a template is for. The engine calls the five steps in order and
never looks inside them.

Parameters (all optional, all with sensible defaults):
    background  "dark" (default) or "white"
    tint        RGB list for the glass, for example [0.85, 0.92, 1.0]
"""

import math

import bpy

from engine.template_api import RenderSettings, Template, TemplateContext

SPHERE_RADIUS = 1.0
SPHERE_HEIGHT = SPHERE_RADIUS + 0.02

BACKGROUNDS = {
    "dark": {"world": (0.015, 0.016, 0.02, 1.0), "floor": (0.05, 0.05, 0.06, 1.0)},
    "white": {"world": (0.85, 0.85, 0.87, 1.0), "floor": (0.72, 0.72, 0.74, 1.0)},
}


class DefaultSphereTemplate(Template):

    name = "DefaultSphere"
    description = "Clean studio setup with a single glass sphere on a neutral backdrop."

    def configure_environment(self, context: TemplateContext) -> None:
        bpy.ops.wm.read_factory_settings(use_empty=True)
        palette = BACKGROUNDS.get(str(context.parameter("background", "dark")).lower(), BACKGROUNDS["dark"])

        scene = bpy.context.scene
        world = bpy.data.worlds.new("StudioWorld")
        world.use_nodes = True
        background = world.node_tree.nodes.get("Background")
        if background is not None:
            background.inputs[0].default_value = palette["world"]
            background.inputs[1].default_value = 1.0
        scene.world = world

        bpy.ops.mesh.primitive_plane_add(size=40.0, location=(0.0, 0.0, 0.0))
        floor = bpy.context.active_object
        floor.name = "StudioFloor"
        floor.data.materials.append(_floor_material(palette["floor"]))

    def create_objects(self, context: TemplateContext) -> None:
        bpy.ops.mesh.primitive_uv_sphere_add(
            radius=SPHERE_RADIUS, location=(0.0, 0.0, SPHERE_HEIGHT), segments=64, ring_count=32)
        sphere = bpy.context.active_object
        sphere.name = "GlassSphere"
        bpy.ops.object.shade_smooth()

        tint = context.parameter("tint")
        sphere.data.materials.append(_glass_material(tint))

    def configure_camera(self, context: TemplateContext) -> None:
        scene = bpy.context.scene
        camera_data = bpy.data.cameras.new("StudioCamera")
        camera_data.lens = 65.0
        camera = bpy.data.objects.new("StudioCamera", camera_data)
        # Slightly above the sphere, tilted down onto it, framed for portrait.
        camera.location = (0.0, -5.5, 1.6)
        camera.rotation_euler = (math.radians(84.3), 0.0, 0.0)
        scene.collection.objects.link(camera)
        scene.camera = camera

    def configure_lighting(self, context: TemplateContext) -> None:
        _add_area_light("KeyLight", energy=900.0, size=7.0,
                        location=(2.6, -3.2, 4.6), rotation=(math.radians(42.0), 0.0, math.radians(35.0)))
        _add_area_light("FillLight", energy=220.0, size=9.0,
                        location=(-3.6, -2.4, 2.2), rotation=(math.radians(72.0), 0.0, math.radians(-52.0)))
        _add_area_light("RimLight", energy=650.0, size=4.0,
                        location=(0.0, 3.4, 3.2), rotation=(math.radians(126.0), 0.0, 0.0))

    def prepare_for_rendering(self, context: TemplateContext) -> None:
        scene = bpy.context.scene
        scene.render.film_transparent = False
        cycles = getattr(scene, "cycles", None)
        if cycles is not None:
            # Glass is refraction, and refraction is bounces. Without these the sphere renders black.
            cycles.max_bounces = 24
            cycles.transmission_bounces = 16
            cycles.transparent_max_bounces = 16
            cycles.use_denoising = True

    def render_settings(self, context: TemplateContext) -> RenderSettings:
        # Denoising carries the low sample count, which keeps a CPU render inside a sane budget.
        return RenderSettings(samples=64)


def _add_area_light(name: str, energy: float, size: float, location: tuple, rotation: tuple) -> None:
    light_data = bpy.data.lights.new(name, type="AREA")
    light_data.energy = energy
    light_data.size = size
    light = bpy.data.objects.new(name, light_data)
    light.location = location
    light.rotation_euler = rotation
    bpy.context.scene.collection.objects.link(light)


def _floor_material(colour: tuple):
    material = bpy.data.materials.new("StudioFloor")
    material.use_nodes = True
    shader = material.node_tree.nodes.get("Principled BSDF")
    _set_input(shader, ("Base Color",), colour)
    _set_input(shader, ("Roughness",), 0.45)
    _set_input(shader, ("Metallic",), 0.0)
    return material


def _glass_material(tint):
    material = bpy.data.materials.new("StudioGlass")
    material.use_nodes = True
    shader = material.node_tree.nodes.get("Principled BSDF")
    colour = (1.0, 1.0, 1.0, 1.0)
    if isinstance(tint, (list, tuple)) and len(tint) >= 3:
        colour = (float(tint[0]), float(tint[1]), float(tint[2]), 1.0)
    _set_input(shader, ("Base Color",), colour)
    _set_input(shader, ("Transmission Weight", "Transmission"), 1.0)
    _set_input(shader, ("Roughness",), 0.02)
    _set_input(shader, ("Metallic",), 0.0)
    _set_input(shader, ("IOR",), 1.45)
    return material


def _set_input(shader, names: tuple, value) -> None:
    """Sets the first input that exists.

    Principled BSDF input names move between Blender versions - 'Transmission' became 'Transmission
    Weight' in 4.0 - so templates ask for the first name that is actually there.
    """
    if shader is None:
        return
    for name in names:
        if name in shader.inputs:
            shader.inputs[name].default_value = value
            return


TEMPLATE = DefaultSphereTemplate()
