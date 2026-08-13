"""Applies render settings and produces the output file.

The renderer is template-agnostic: it takes the settings a template declared and the path Java asked
for, and does the same thing every time. A template that declares a duration gets a movie, one that
does not gets a still - there is no per-template branching here and never should be.

Blender specifics that live here on purpose, because they are output concerns rather than scene
concerns: the render engine identifier, the H.264 container settings, and the fact that Blender likes
to decorate movie filenames with a frame range.
"""

import glob
import os
from dataclasses import dataclass

import bpy

from .template_api import RenderSettings

IMAGE_EXTENSIONS = {
    "PNG": ".png",
    "JPEG": ".jpg",
    "OPEN_EXR": ".exr",
}

MOVIE_FORMAT = "FFMPEG"

# Logical engine names templates use, mapped to the identifiers Blender actually offers. EEVEE was
# renamed in 4.2, so the first identifier that exists in this build wins.
ENGINE_IDENTIFIERS = {
    "EEVEE": ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE"),
    "CYCLES": ("CYCLES",),
    "WORKBENCH": ("BLENDER_WORKBENCH",),
}


class RenderError(Exception):
    """Blender finished but did not produce the file it was asked for."""


@dataclass(frozen=True)
class RenderOutcome:
    output_path: str
    resolution: str
    fps: int
    duration_seconds: float
    frames: int


class Renderer:

    def render(self, settings: RenderSettings, output_path: str) -> RenderOutcome:
        # Relative paths are resolved against the working directory, which Java sets to the workspace
        # root, so the contract means the same thing on both sides of the boundary.
        absolute = os.path.abspath(output_path)
        os.makedirs(os.path.dirname(absolute), exist_ok=True)

        scene = bpy.context.scene
        self._apply(scene, settings)

        if settings.is_animation:
            produced = self._render_animation(scene, settings, absolute)
        else:
            produced = self._render_still(scene, settings, absolute)

        return RenderOutcome(produced, settings.resolution, settings.fps,
                             settings.duration_seconds, settings.frames if settings.is_animation else 1)

    def _render_still(self, scene, settings: RenderSettings, absolute: str) -> str:
        # Blender appends the extension itself, so hand it the path without one and get back exactly
        # the file Java is going to look for.
        stem = os.path.splitext(absolute)[0]
        scene.render.use_file_extension = True
        scene.render.image_settings.file_format = settings.file_format
        scene.render.filepath = stem
        expected = stem + IMAGE_EXTENSIONS.get(settings.file_format, ".png")

        bpy.ops.render.render(write_still=True)

        if not os.path.isfile(expected):
            raise RenderError("Blender wrote no image to " + expected)
        return expected

    def _render_animation(self, scene, settings: RenderSettings, absolute: str) -> str:
        scene.frame_start = 1
        scene.frame_end = settings.frames
        scene.render.fps = settings.fps
        scene.render.fps_base = 1.0
        self._configure_h264(scene)

        # Movie output keeps the path verbatim only when Blender is told not to decorate it.
        scene.render.use_file_extension = False
        scene.render.filepath = absolute

        bpy.ops.render.render(animation=True)

        return self._locate_movie(absolute)

    @staticmethod
    def _configure_h264(scene) -> None:
        image_settings = scene.render.image_settings

        # Blender 4.5 split stills from movies: the movie formats only appear in the file_format
        # enum once the media type says video. Older builds have no media_type and list them always.
        _try_set(image_settings, "media_type", "VIDEO")
        try:
            image_settings.file_format = MOVIE_FORMAT
        except TypeError as error:
            raise RenderError(
                "This Blender cannot write movies - '{0}' was rejected as an output format. It is "
                "probably built without FFmpeg support. ({1})".format(MOVIE_FORMAT, error))

        # H.264 has no alpha channel; the still default of RGBA is invalid here.
        _try_set(image_settings, "color_mode", "RGB")

        ffmpeg = getattr(scene.render, "ffmpeg", None)
        if ffmpeg is None:
            raise RenderError("This Blender has no FFmpeg output settings, so it cannot write MP4.")
        _set(ffmpeg, "format", "MPEG4")
        _set(ffmpeg, "codec", "H264")
        _set(ffmpeg, "constant_rate_factor", "HIGH")
        _set(ffmpeg, "ffmpeg_preset", "GOOD")
        _set(ffmpeg, "gopsize", int(scene.render.fps))
        _set(ffmpeg, "audio_codec", "NONE")

    @staticmethod
    def _locate_movie(absolute: str) -> str:
        """Normalises Blender's movie file name to the path the contract asked for.

        Depending on version and settings Blender may write 'clip0001-0600.mp4' next to the requested
        'clip.mp4'. Renaming it here means the contract, the manifest and Java all agree on one path.
        """
        if os.path.isfile(absolute):
            return absolute

        stem, extension = os.path.splitext(absolute)
        candidates = sorted(glob.glob(stem + "*" + extension))
        if not candidates:
            raise RenderError("Blender wrote no movie to {0} (directory contains: {1})".format(
                absolute, ", ".join(sorted(os.listdir(os.path.dirname(absolute)))) or "nothing"))

        os.replace(candidates[0], absolute)
        print("render.renamed=" + os.path.basename(candidates[0]) + "->" + os.path.basename(absolute))
        return absolute

    @staticmethod
    def _apply(scene, settings: RenderSettings) -> None:
        render = scene.render
        render.resolution_x = settings.width
        render.resolution_y = settings.height
        render.resolution_percentage = 100
        render.image_settings.color_mode = settings.color_mode
        render.engine = _resolve_engine(scene, settings.engine)

        cycles = getattr(scene, "cycles", None)
        if render.engine == "CYCLES" and cycles is not None:
            cycles.device = settings.device
            cycles.samples = settings.samples

        eevee = getattr(scene, "eevee", None)
        if render.engine.startswith("BLENDER_EEVEE") and eevee is not None:
            _set(eevee, "taa_render_samples", settings.samples)
            # Refraction for glass: raytracing in 4.2+, screen space reflections before that.
            _set(eevee, "use_raytracing", True)
            _set(eevee, "use_ssr", True)
            _set(eevee, "use_ssr_refraction", True)


def _resolve_engine(scene, requested: str) -> str:
    available = {item.identifier for item in scene.render.bl_rna.properties["engine"].enum_items}
    for identifier in ENGINE_IDENTIFIERS.get(requested, (requested,)):
        if identifier in available:
            return identifier
    raise RenderError("No render engine for '{0}'; this Blender offers {1}".format(
        requested, ", ".join(sorted(available))))


def _set(target, name: str, value) -> None:
    """Sets a property when this Blender version has it; output settings move between releases."""
    if hasattr(target, name):
        setattr(target, name, value)


def _try_set(target, name: str, value) -> bool:
    """Sets a property that may not exist, or may not accept this value in this Blender version."""
    if not hasattr(target, name):
        return False
    try:
        setattr(target, name, value)
        return True
    except TypeError:
        return False
