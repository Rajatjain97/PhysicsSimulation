"""Physics Reel Studio - the Blender entry point for a render.

Run by Java as:

    blender --background --python render_scene.py -- \
        --scene <contract.json> --engine <engine dir> --templates <templates dir> \
        --assets <assets dir> --render-id <id> [--quality PRODUCTION|FAST]

Deliberately thin. This file parses arguments, puts the engine package on the import path and hands
over; every decision worth reviewing lives in the engine modules or in a template. If this script
starts growing rendering logic, that logic belongs somewhere else.
"""

import argparse
import os
import sys

ENGINE_PACKAGE = "engine"


def main() -> None:
    arguments = _parse_arguments(_script_arguments())
    engine = _import_engine(arguments.engine)

    contract = engine["scene_contract"].SceneContract.load(arguments.scene)
    templates = engine["template_registry"].TemplateRegistry(arguments.templates)
    assets = engine["asset_registry"].AssetRegistry(arguments.assets)
    renderer = engine["renderer"].Renderer(arguments.quality)
    builder = engine["scene_builder"].SceneBuilder(templates, assets, renderer)

    builder.build_and_render(contract, arguments.render_id)


def _script_arguments() -> list:
    """Blender passes everything after '--' to the script."""
    if "--" not in sys.argv:
        return []
    return sys.argv[sys.argv.index("--") + 1:]


def _parse_arguments(argv: list) -> argparse.Namespace:
    parser = argparse.ArgumentParser(prog="render_scene.py")
    parser.add_argument("--scene", required=True, help="path to the scene contract JSON")
    parser.add_argument("--engine", required=True, help="path to the engine package directory")
    parser.add_argument("--templates", required=True, help="path to the templates directory")
    parser.add_argument("--assets", required=True, help="path to the shared asset library directory")
    parser.add_argument("--render-id", required=True, dest="render_id", help="identity assigned by Java")
    parser.add_argument("--quality", default="PRODUCTION",
                        help="how much rendering cost to pay: PRODUCTION or FAST")
    return parser.parse_args(argv)


def _import_engine(engine_directory: str) -> dict:
    """Imports the engine as a package, so its modules and the templates share one import root."""
    engine_directory = os.path.abspath(engine_directory)
    if not os.path.isdir(engine_directory):
        raise SystemExit("Engine directory not found: " + engine_directory)
    if os.path.basename(engine_directory) != ENGINE_PACKAGE:
        raise SystemExit(
            "The engine directory must be named '{0}' because templates import from it, but was: {1}".format(
                ENGINE_PACKAGE, engine_directory))

    import importlib

    root = os.path.dirname(engine_directory)
    if root not in sys.path:
        sys.path.insert(0, root)
    return {
        name: importlib.import_module(ENGINE_PACKAGE + "." + name)
        for name in ("scene_contract", "template_registry", "asset_registry", "renderer", "scene_builder")
    }


if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as error:  # surfaced to Java as a non-zero exit with a readable reason
        raise SystemExit("{0}: {1}".format(type(error).__name__, error))
