"""Orchestration, and nothing else.

    resolve template -> template builds the scene -> renderer renders -> manifest recorded

The scene builder does not know how any scene is constructed. That knowledge lives in template
modules, which is what makes a new scene type a new directory rather than an edit here.

The key=value lines are printed on purpose: they are the render's stdout contract with Java, which
logs them and asserts on them.
"""

from .render_manifest import write_manifest
from .renderer import Renderer
from .scene_contract import SceneContract
from .template_api import TemplateContext
from .template_registry import TemplateRegistry


class SceneBuilder:

    def __init__(self, registry: TemplateRegistry, renderer: Renderer):
        self._registry = registry
        self._renderer = renderer

    def build_and_render(self, contract: SceneContract, render_id: str) -> str:
        """Renders the contract and returns the path of the manifest that was written."""
        descriptor = self._registry.resolve(contract.template)
        print("render.template=" + descriptor.name)

        context = TemplateContext(parameters=dict(contract.parameters))
        descriptor.template.build(context)
        print("render.scene=built")

        settings = descriptor.template.render_settings(context)
        outcome = self._renderer.render(settings, contract.image_output)
        print("render.resolution=" + outcome.resolution)
        print("render.output=" + outcome.image_path)

        manifest_path = write_manifest(outcome.image_path, render_id, contract, outcome.resolution)
        print("render.manifest=" + manifest_path)
        print("render.status=completed")
        return manifest_path
