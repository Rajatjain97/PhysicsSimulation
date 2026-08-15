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
from .timing import measure

IMAGE_EXTENSIONS = {
    "PNG": ".png",
    "JPEG": ".jpg",
    "OPEN_EXR": ".exr",
}

MOVIE_FORMAT = "FFMPEG"

MINIMUM_SAMPLES = 4


@dataclass(frozen=True)
class RenderQuality:
    """How much of a template's declared quality to actually pay for.

    Two things a quality must never change, both learned the hard way:

    * **The simulation.** The solver's timestep is one scene frame, so frame rate and frame count are
      simulation inputs, not render settings. Lowering either would produce a different reel rather
      than a cheaper view of the same one.
    * **What the scene is made of.** Switching raytracing off cost every glass marble its colour:
      a transmissive material has nothing to refract without it, and a preview that misrepresents
      the materials is not a preview. Refraction stays on at every quality; only the resolution it
      is traced at varies.

    So a quality only ever scales cost: how many pixels, how many samples, and how finely the
    raytracing pass is traced.
    """

    name: str
    resolution_percentage: int
    sample_scale: float
    trace_divisor: int

    def samples_for(self, declared: int) -> int:
        return max(MINIMUM_SAMPLES, int(round(declared * self.sample_scale)))


QUALITIES = {
    # What gets published: full resolution, the template's samples, tracing at full resolution.
    "PRODUCTION": RenderQuality("PRODUCTION", 100, 1.0, 1),
    # What a template author watches while iterating: a quarter of the pixels, half the samples, and
    # refraction traced at a quarter resolution. Same composition, same physics, same timing, same
    # materials - a smaller, softer version of the real reel rather than a different one.
    "FAST": RenderQuality("FAST", 50, 0.5, 4),
}

DEFAULT_QUALITY = "PRODUCTION"


def quality(name: str) -> RenderQuality:
    found = QUALITIES.get(str(name).upper())
    if found is None:
        raise RenderError("Unknown render quality '{0}'. This engine knows: {1}".format(
            name, ", ".join(sorted(QUALITIES))))
    return found

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

    def __init__(self, quality_name: str = DEFAULT_QUALITY):
        self._quality = quality(quality_name)
        # What the raytracing pass was actually traced at, reported rather than assumed: the control
        # is version specific, and a render should say what it really did.
        self._traced_at = "n/a"

    def render(self, settings: RenderSettings, output_path: str) -> RenderOutcome:
        # Relative paths are resolved against the working directory, which Java sets to the workspace
        # root, so the contract means the same thing on both sides of the boundary.
        absolute = os.path.abspath(output_path)
        os.makedirs(os.path.dirname(absolute), exist_ok=True)

        scene = bpy.context.scene
        self._apply(scene, settings)
        # What actually did the work, so a slow render's own log says which engine and which device
        # to blame. EEVEE is rasterised on the GPU; only Cycles has a device to choose.
        print("render.quality={0} engine={1} device={2} samples={3} scale={4}% traced=1:{5}".format(
            self._quality.name, scene.render.engine,
            getattr(getattr(scene, "cycles", None), "device", "GPU (rasterised)")
            if scene.render.engine == "CYCLES" else "GPU (rasterised)",
            self._quality.samples_for(settings.samples), self._quality.resolution_percentage,
            self._traced_at))

        # Frame rendering and H.264 encoding both happen inside one Blender call, so they cannot be
        # timed apart without rendering frame by frame. This measures them together.
        with measure("render"):
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

    def _apply(self, scene, settings: RenderSettings) -> None:
        render = scene.render
        render.resolution_x = settings.width
        render.resolution_y = settings.height
        render.resolution_percentage = self._quality.resolution_percentage
        render.image_settings.color_mode = settings.color_mode
        render.engine = _resolve_engine(scene, settings.engine)

        samples = self._quality.samples_for(settings.samples)
        cycles = getattr(scene, "cycles", None)
        if render.engine == "CYCLES" and cycles is not None:
            cycles.device = settings.device
            cycles.samples = samples

        self._traced_at = "n/a"
        eevee = getattr(scene, "eevee", None)
        if render.engine.startswith("BLENDER_EEVEE") and eevee is not None:
            _set(eevee, "taa_render_samples", samples)
            # Refraction for glass: raytracing in 4.2+, screen space reflections before that. Always
            # on - the glass materials are transmissive and render colourless without it.
            _set(eevee, "use_raytracing", True)
            _set(eevee, "use_ssr", True)
            _set(eevee, "use_ssr_refraction", True)
            self._traced_at = _trace_resolution(eevee, self._quality.trace_divisor)


def _trace_resolution(eevee, divisor: int) -> str:
    """Traces raytracing at a fraction of the render resolution, when the build allows it.

    Cheaper than switching raytracing off and honest in a way that switching it off is not: glass
    still refracts and still carries its colour, it is simply resolved more coarsely. The control is
    EEVEE Next's and is spelled differently across releases, so what actually took effect is returned
    rather than assumed.
    """
    if divisor <= 1:
        return "1"
    options = getattr(eevee, "ray_tracing_options", None)
    if options is not None and _try_set(options, "resolution_scale", str(divisor)):
        return str(divisor)
    # Older EEVEE traces screen space at a fixed resolution; the sample and pixel cuts still apply.
    return "1 (this build has no trace resolution control)"


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
