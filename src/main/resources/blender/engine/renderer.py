"""Applies render settings and produces the image.

The renderer is template-agnostic: it takes the settings a template declared and the path Java asked
for, and does the same thing every time. When MP4 arrives it gains a sibling method here; no template
and no Java code changes.
"""

import os
from dataclasses import dataclass

import bpy

from .template_api import RenderSettings

FILE_EXTENSIONS = {
    "PNG": ".png",
    "JPEG": ".jpg",
    "OPEN_EXR": ".exr",
}


class RenderError(Exception):
    """Blender finished but did not produce the file it was asked for."""


@dataclass(frozen=True)
class RenderOutcome:
    image_path: str
    resolution: str


class Renderer:

    def render(self, settings: RenderSettings, image_output: str) -> RenderOutcome:
        # Relative paths are resolved against the working directory, which Java sets to the workspace
        # root, so the contract means the same thing on both sides of the boundary.
        absolute = os.path.abspath(image_output)
        os.makedirs(os.path.dirname(absolute), exist_ok=True)

        scene = bpy.context.scene
        self._apply(scene, settings)

        # Blender appends the extension itself, so hand it the path without one and get back exactly
        # the file Java is going to look for.
        stem = os.path.splitext(absolute)[0]
        scene.render.filepath = stem
        expected = stem + FILE_EXTENSIONS.get(settings.file_format, ".png")

        bpy.ops.render.render(write_still=True)

        if not os.path.isfile(expected):
            raise RenderError("Blender wrote no image to " + expected)
        return RenderOutcome(expected, settings.resolution)

    @staticmethod
    def _apply(scene, settings: RenderSettings) -> None:
        render = scene.render
        render.resolution_x = settings.width
        render.resolution_y = settings.height
        render.resolution_percentage = 100
        render.use_file_extension = True
        render.image_settings.file_format = settings.file_format
        render.image_settings.color_mode = settings.color_mode
        render.engine = settings.engine

        cycles = getattr(scene, "cycles", None)
        if settings.engine == "CYCLES" and cycles is not None:
            cycles.device = settings.device
            cycles.samples = settings.samples
