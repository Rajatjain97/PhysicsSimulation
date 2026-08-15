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
from .template_api import TemplateContext
from .template_registry import TemplateRegistry


class SceneBuilder:

    def __init__(self, registry: TemplateRegistry, assets: AssetRegistry, renderer: Renderer):
        self._registry = registry
        self._assets = assets
        self._renderer = renderer

    def build_and_render(self, contract: SceneContract, render_id: str) -> str:
        """Renders the contract and returns the path of the manifest that was written."""
        descriptor = self._registry.resolve(contract.template)
        print("render.template=" + descriptor.name)

        # One seed decides every random choice in the scene. Java puts it in the contract, so a
        # manifest is enough to rebuild this exact reel.
        randomness = RandomContext.from_parameters(contract.parameters)
        print("render.seed=" + str(randomness.seed))
        context = TemplateContext(parameters=dict(contract.parameters), assets=self._assets,
                                  random=randomness)

        settings = descriptor.template.render_settings(context)

        # A template describes what happens; the director carries it out. build() runs first because
        # it establishes the environment - including resetting the scene - that events land in.
        descriptor.template.build(context)

        timeline = descriptor.template.timeline(context)
        if not timeline.is_empty():
            print("render.timeline=" + timeline.summary())
            directed = SceneDirector(self._assets, settings).direct(timeline)
            print("render.directed=" + str(directed))
        materials = self._assets.materials.resolved_names()
        print("render.materials=" + (",".join(materials) or "(none)"))
        print("render.scene=built")

        outcome = self._renderer.render(settings, contract.output_path)
        print("render.resolution=" + outcome.resolution)
        print("render.fps=" + str(outcome.fps))
        print("render.frames=" + str(outcome.frames))
        print("render.output=" + outcome.output_path)

        manifest_path = write_manifest(outcome, render_id, contract, materials)
        print("render.manifest=" + manifest_path)
        print("render.status=completed")
        return manifest_path
