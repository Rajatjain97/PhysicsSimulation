"""Orchestration, and nothing else.

    resolve template -> template builds the scene -> renderer renders -> manifest recorded

The scene builder does not know how any scene is constructed, nor how any material is made. That
knowledge lives in template modules and asset definitions, which is what makes a new scene type a new
directory rather than an edit here.

The key=value lines are printed on purpose: they are the render's stdout contract with Java, which
logs them and asserts on them.
"""

from .asset_registry import AssetRegistry
from .render_manifest import write_manifest
from .renderer import Renderer
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

        context = TemplateContext(parameters=dict(contract.parameters), assets=self._assets)
        descriptor.template.build(context)
        materials = self._assets.materials.resolved_names()
        print("render.materials=" + (",".join(materials) or "(none)"))
        print("render.scene=built")

        settings = descriptor.template.render_settings(context)
        outcome = self._renderer.render(settings, contract.output_path)
        print("render.resolution=" + outcome.resolution)
        print("render.fps=" + str(outcome.fps))
        print("render.frames=" + str(outcome.frames))
        print("render.output=" + outcome.output_path)

        manifest_path = write_manifest(outcome, render_id, contract, materials)
        print("render.manifest=" + manifest_path)
        print("render.status=completed")
        return manifest_path
