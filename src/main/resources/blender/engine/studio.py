"""The Blender mechanics every template repeats when it sets up a studio.

Four templates were each carrying their own copy of "make an area light" and "give the scene a
coloured world", identical apart from the values they passed in. The mechanics are the same every
time; the values are the entire creative difference between one reel and another.

So this module owns the mechanism and nothing else. It creates a light, it configures a world - it
has no opinion about how many lights a scene needs, where they go, how bright they are, or what
colour the background is. Those decisions stay in the template that makes them, which is why there is
no studio preset here and should not be one: two templates that light a scene identically are a
coincidence, not a shared requirement.
"""

import bpy

BACKGROUND_NODE = "Background"
AREA = "AREA"


def create_area_light(scene, name: str, energy: float, size: float, location, rotation):
    """Creates one area light, places it, and links it to the scene.

    Every parameter is the caller's decision. Returns the light object so a template can reach for
    anything this does not cover.
    """
    light_data = bpy.data.lights.new(name, type=AREA)
    light_data.energy = energy
    light_data.size = size
    # Shadows are what make an impact read as contact rather than an overlay. Guarded because the
    # switch moves between Blender releases.
    if hasattr(light_data, "use_shadow"):
        light_data.use_shadow = True

    light = bpy.data.objects.new(name, light_data)
    light.location = location
    light.rotation_euler = rotation
    scene.collection.objects.link(light)
    return light


def set_world_background(scene, name: str, colour, strength: float = 1.0):
    """Gives the scene a new world with a flat background colour, and returns it.

    A fresh world rather than an edit of whatever was there: templates reset the file before they
    build, so there is nothing worth preserving, and creating one keeps the name predictable.
    """
    world = bpy.data.worlds.new(name)
    world.use_nodes = True

    background = world.node_tree.nodes.get(BACKGROUND_NODE)
    if background is not None:
        background.inputs[0].default_value = colour
        background.inputs[1].default_value = strength

    scene.world = world
    return world
