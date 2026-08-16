"""Orchestration, and nothing else.

    resolve template -> template builds the scene -> renderer renders -> manifest recorded

The scene builder does not know how any scene is constructed, nor how any material is made. That
knowledge lives in template modules and asset definitions, which is what makes a new scene type a new
directory rather than an edit here.

The key=value lines are printed on purpose: they are the render's stdout contract with Java, which
logs them and asserts on them.
"""

from .asset_registry import AssetRegistry
from .randomness import RandomContext
from .render_manifest import write_manifest
from .renderer import Renderer
from .scene_director import SceneDirector
from .scene_contract import SceneContract
from .template_api import DEFAULT_HOLD_SECONDS, MAX_REEL_SECONDS, DurationPlan, TemplateContext
from .template_registry import TemplateRegistry
from .timing import measure


class SceneBuilder:

    def __init__(self, registry: TemplateRegistry, assets: AssetRegistry, renderer: Renderer):
        self._registry = registry
        self._assets = assets
        self._renderer = renderer

    def build_and_render(self, contract: SceneContract, render_id: str) -> str:
        """Renders the contract and returns the path of the manifest that was written.

        Each stage prints how long it took. Java measures the whole Blender process, so the gap
        between its measurement and "timing.total" here is Blender's own startup and teardown - a
        cost this side cannot see, and the only one that has to be inferred.
        """
        with measure("total"):
            return self._build_and_render(contract, render_id)

    def _build_and_render(self, contract: SceneContract, render_id: str) -> str:
        descriptor = self._registry.resolve(contract.template)
        print("render.template=" + descriptor.name)

        # One seed decides every random choice in the scene. Java puts it in the contract, so a
        # manifest is enough to rebuild this exact reel.
        randomness = RandomContext.from_parameters(contract.parameters)
        print("render.seed=" + str(randomness.seed))
        context = TemplateContext(parameters=dict(contract.parameters), assets=self._assets,
                                  random=randomness)

        settings = _with_duration_limits(descriptor.template.render_settings(context),
                                         contract.parameters)

        # A template describes what happens; the director carries it out. build() runs first because
        # it establishes the environment - including resetting the scene - that events land in.
        with measure("scene"):
            descriptor.template.build(context)

            timeline = descriptor.template.timeline(context)
            direction = None
            if not timeline.is_empty():
                print("render.timeline=" + timeline.summary())
                # Physics runs inside this, and times itself separately.
                direction = SceneDirector(self._assets, settings).direct(timeline)
                print("render.directed=" + str(direction.events))
        plan = _duration_plan(settings, direction)
        if plan is not None:
            print("render.duration=" + plan.summary())

        materials = self._assets.materials.resolved_names()
        print("render.materials=" + (",".join(materials) or "(none)"))
        print("render.scene=built")

        outcome = self._renderer.render(settings, contract.output_path, plan)
        print("render.resolution=" + outcome.resolution)
        print("render.fps=" + str(outcome.fps))
        print("render.frames=" + str(outcome.frames))
        print("render.output=" + outcome.output_path)

        manifest_path = write_manifest(outcome, render_id, contract, materials, plan)
        print("render.manifest=" + manifest_path)
        print("render.status=completed")
        return manifest_path


def _with_duration_limits(settings, parameters):
    """Applies the operator's duration limits to what the template declared.

    Both are engine concerns rather than template ones - every reel holds after its content, and
    every reel needs a ceiling - so they are read here, once, instead of in every template.
    """
    return settings.with_limits(
        hold_seconds=_number(parameters, "postEventHoldSeconds", DEFAULT_HOLD_SECONDS),
        max_duration_seconds=_number(parameters, "maxDurationSeconds", MAX_REEL_SECONDS))


def _duration_plan(settings, direction):
    """Turns what the simulation did into the length of the video.

    No simulated content means no plan: a still, or a template that animates nothing, is exactly as
    long as it asked to be, and adding a hold to it would only add dead frames.
    """
    if direction is None or direction.content_end_frame is None or not settings.is_animation:
        return None
    content_frames = min(direction.content_end_frame, settings.budget_frames)
    return DurationPlan(fps=settings.fps, requested_seconds=settings.duration_seconds,
                        content_frames=content_frames,
                        hold_frames=min(settings.hold_frames, settings.budget_frames - content_frames),
                        settled=direction.settled)


def _number(parameters, name, default):
    value = parameters.get(name, default)
    try:
        return float(value)
    except (TypeError, ValueError):
        raise ValueError("Parameter '{0}' must be a number but was {1!r}".format(name, value))
