"""Physics Reel Studio - the Blender-side rendering engine.

Java orchestrates and hands over a scene contract; everything from that point on lives here. The
engine knows how to read a contract, find a template, let the template build a scene, render it and
record what it produced. It knows nothing about any individual template.

Modules:
    scene_contract      - the contract Java writes, parsed and validated
    template_api        - the interface every template implements
    template_registry   - discovery, validation and resolution of templates
    asset_api           - the interface every shared asset implements
    material_repository - discovery, resolution and lifetime of shared materials
    asset_registry      - the handle templates use to reach shared assets
    renderer            - applies render settings and produces the image
    render_manifest     - records what was rendered, beside the output
    scene_builder       - orchestration: contract in, manifest out

Adding a scene type means adding a template module; adding a shared material means adding an asset
module. Nothing in this package changes.
"""

SCHEMA_VERSION = "1.0"
