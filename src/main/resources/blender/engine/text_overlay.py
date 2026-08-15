"""Text on screen.

A template says what the caption is, when it appears and how it should read - Hook, Winner, Subtitle
- and this module decides everything else: which font object, how big, where in frame, how it fades.
Nothing else in the engine writes text.

The overlay is a Blender text object parented to the camera rather than a mesh or a compositor pass.
That choice does the work for us: parenting means the caption follows any camera preset, including
the moving ones, without a template ever asking where the camera is; text objects stay crisp at any
resolution; and an emissive material makes captions readable regardless of scene lighting.

Sizes are fractions of the frame, never pixels, so a caption occupies the same share of a Reel
whatever the render resolution is. Adding a style is one entry in STYLES.
"""

from dataclasses import dataclass
from typing import Dict, Optional, Tuple

import bpy

from .asset_api import set_material_flag, set_shader_input

# How far in front of the camera captions sit. Any distance works - size is derived from it - but
# close enough to stay in front of the scene, far enough not to clip the near plane.
OVERLAY_DISTANCE = 3.0
SENSOR_HALF_WIDTH = 18.0

# Vertical placement as a fraction of frame height from the centre. Inside the safe area, so nothing
# collides with a platform's own interface furniture.
POSITIONS: Dict[str, float] = {
    "Top": 0.34,
    "Center": 0.0,
    "Bottom": -0.34,
}
DEFAULT_POSITION = "Center"


class TextOverlayError(Exception):
    """The caption cannot be placed."""


@dataclass(frozen=True)
class TextStyle:
    """How a caption reads.

    :param height:   cap height as a fraction of the frame height
    :param colour:   emissive colour
    :param strength: emission strength; higher reads as brighter against a dark scene
    :param fade:     seconds to fade in, and to fade out
    :param position: where it sits unless the event says otherwise
    """

    height: float
    colour: Tuple[float, float, float]
    strength: float = 3.0
    fade: float = 0.35
    position: str = DEFAULT_POSITION


STYLES: Dict[str, TextStyle] = {
    # The opening line. Big enough to read at a glance while scrolling.
    "Hook": TextStyle(height=0.075, colour=(1.0, 1.0, 1.0), strength=4.0, fade=0.4, position="Top"),
    # Supporting line, quieter and lower.
    "Subtitle": TextStyle(height=0.042, colour=(0.86, 0.89, 0.95), strength=2.2, fade=0.3, position="Bottom"),
    # The payoff. Warm and large, centred on the action.
    "Winner": TextStyle(height=0.095, colour=(1.0, 0.82, 0.28), strength=6.0, fade=0.25, position="Center"),
    "Default": TextStyle(height=0.05, colour=(1.0, 1.0, 1.0)),
}

DEFAULT_STYLE = "Default"


@dataclass(frozen=True)
class TextRequest:
    """One caption: what it says, when it is on screen, and how it reads."""

    text: str
    style: str = DEFAULT_STYLE
    position: Optional[str] = None
    at: float = 0.0
    duration: Optional[float] = None
    name: Optional[str] = None


def show(request: TextRequest, scene, fps: int, frames: int):
    """Creates a caption and animates it in and out. Returns the text object."""
    if not request.text:
        raise TextOverlayError("A caption needs something to say")

    style = STYLES.get(request.style)
    if style is None:
        raise TextOverlayError("Unknown text style '{0}'. This engine knows: {1}".format(
            request.style, ", ".join(style_names())))

    position = request.position or style.position
    if position not in POSITIONS:
        raise TextOverlayError("Unknown text position '{0}'. This engine knows: {1}".format(
            position, ", ".join(sorted(POSITIONS))))

    camera = scene.camera
    if camera is None:
        raise TextOverlayError(
            "A caption is placed relative to the camera, so the camera event must come first")

    frame_height = _frame_height(camera)
    caption = _create(request, style, scene)
    _place(caption, camera, frame_height * POSITIONS[position], frame_height * style.height)
    _fade(caption, style, request, scene, fps, frames)
    return caption


def style_names() -> list:
    return sorted(STYLES)


def _create(request: TextRequest, style: TextStyle, scene):
    data = bpy.data.curves.new(name=request.name or "Caption", type="FONT")
    data.body = request.text
    data.align_x = "CENTER"
    data.align_y = "CENTER"

    caption = bpy.data.objects.new(request.name or "Caption", data)
    scene.collection.objects.link(caption)
    caption.data.materials.append(_material(style, request.style))
    return caption


def _place(caption, camera, vertical_offset: float, size: float) -> None:
    """Parents the caption to the camera so it travels with any shot, moving or not."""
    caption.data.size = size
    caption.parent = camera
    caption.matrix_parent_inverse = camera.matrix_world.inverted()
    # Camera space: -Z is where the camera looks, so this sits squarely in front of it, upright.
    caption.location = (0.0, vertical_offset, -OVERLAY_DISTANCE)
    caption.rotation_euler = (0.0, 0.0, 0.0)


def _fade(caption, style: TextStyle, request: TextRequest, scene, fps: int, frames: int) -> None:
    """Fades in, holds, fades out - and is fully transparent outside its window."""
    start = max(1, int(round(request.at * fps)) + 1)
    length = int(round((request.duration if request.duration else style.fade * 2 + 1.0) * fps))
    end = min(frames, start + max(length, 2))

    # A fade cannot be longer than half the caption, or it would never reach full opacity.
    fade = max(1, min(int(round(style.fade * fps)), (end - start) // 2))
    alpha = _alpha_input(caption.data.materials[0])
    if alpha is None:
        return

    for frame, value in ((start, 0.0), (start + fade, 1.0), (end - fade, 1.0), (end, 0.0)):
        alpha.default_value = value
        alpha.keyframe_insert(data_path="default_value", frame=frame)


def _material(style: TextStyle, style_name: str):
    """One emissive material per style, created once and reused by every caption in that style."""
    name = "Caption:{0}".format(style_name)
    material = bpy.data.materials.get(name)
    if material is not None:
        return material

    material = bpy.data.materials.new(name)
    material.use_nodes = True
    shader = material.node_tree.nodes.get("Principled BSDF")
    colour = (style.colour[0], style.colour[1], style.colour[2], 1.0)
    set_shader_input(shader, ("Base Color",), colour)
    set_shader_input(shader, ("Emission Color", "Emission"), colour)
    set_shader_input(shader, ("Emission Strength",), style.strength)
    set_shader_input(shader, ("Roughness",), 1.0)
    set_shader_input(shader, ("Alpha",), 0.0)

    # Alpha only blends if the material is told to. The property was renamed in 4.2, so try both.
    set_material_flag(material, "blend_method", "BLEND")
    set_material_flag(material, "surface_render_method", "BLENDED")
    set_material_flag(material, "use_backface_culling", False)
    return material


def _alpha_input(material):
    shader = material.node_tree.nodes.get("Principled BSDF")
    if shader is None:
        return None
    return shader.inputs["Alpha"] if "Alpha" in shader.inputs else None


def _frame_height(camera) -> float:
    """How tall the frame is, in metres, at the distance captions sit."""
    lens = getattr(camera.data, "lens", 50.0) or 50.0
    return 2.0 * OVERLAY_DISTANCE * SENSOR_HALF_WIDTH / lens
