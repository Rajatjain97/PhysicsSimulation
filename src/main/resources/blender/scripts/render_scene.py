"""Physics Reel Studio - render one scene contract to a still image.

Run by Java as:

    blender --background --python render_scene.py -- --scene <contract.json> --template <template>

Everything visual lives here, on the Blender side of the boundary. Java decides *what* to render and
hands over a contract; this script decides *how*, because it is the only side that knows what a
camera, a material or a sphere is.

Adding support for a new object type is a new entry in _OBJECT_BUILDERS. Adding cameras, materials,
overlays or animation means new sections of the contract read here - not new Java.
"""

import argparse
import json
import os
import sys

import bpy

SUPPORTED_SCENE_VERSION = 1


def main() -> None:
    arguments = _parse_arguments(_script_arguments())
    contract = _read_contract(arguments.scene)

    _load_template(arguments.template)
    for specification in contract.get("objects", []):
        _add_object(specification)

    image_path = _configure_output(contract)
    print("render.template=" + arguments.template)
    print("render.output=" + image_path)

    bpy.ops.render.render(write_still=True)

    if not os.path.isfile(image_path):
        raise SystemExit("Blender finished but wrote no image to " + image_path)
    print("render.status=completed")


def _script_arguments() -> list:
    """Blender passes everything after '--' to the script."""
    if "--" not in sys.argv:
        return []
    return sys.argv[sys.argv.index("--") + 1:]


def _parse_arguments(argv: list) -> argparse.Namespace:
    parser = argparse.ArgumentParser(prog="render_scene.py")
    parser.add_argument("--scene", required=True, help="path to the scene contract JSON")
    parser.add_argument("--template", required=True, help="path to the .blend or .py template")
    return parser.parse_args(argv)


def _read_contract(path: str) -> dict:
    if not os.path.isfile(path):
        raise SystemExit("Scene contract not found: " + path)
    with open(path, "r", encoding="utf-8") as contract_file:
        contract = json.load(contract_file)

    version = contract.get("sceneVersion")
    if version != SUPPORTED_SCENE_VERSION:
        raise SystemExit(
            "Unsupported sceneVersion {0}; this script understands version {1}".format(
                version, SUPPORTED_SCENE_VERSION))
    return contract


def _load_template(path: str) -> None:
    if not os.path.isfile(path):
        raise SystemExit("Template not found: " + path)
    if path.endswith(".blend"):
        bpy.ops.wm.open_mainfile(filepath=path)
        return

    import importlib.util

    specification = importlib.util.spec_from_file_location("physics_reel_template", path)
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    if not hasattr(module, "build"):
        raise SystemExit("Python template " + path + " must define a build() function")
    module.build()


def _add_object(specification: dict) -> None:
    object_type = specification.get("type")
    builder = _OBJECT_BUILDERS.get(object_type)
    if builder is None:
        raise SystemExit("Unsupported object type: " + str(object_type))
    builder(specification)


def _add_sphere(specification: dict) -> None:
    location = tuple(specification.get("location", [0.0, 0.0, 0.0]))
    bpy.ops.mesh.primitive_uv_sphere_add(radius=1.0, location=location, segments=48, ring_count=24)
    sphere = bpy.context.active_object
    bpy.ops.object.shade_smooth()
    sphere.data.materials.append(_simple_material())


def _simple_material():
    material = bpy.data.materials.new("PhysicsReelSurface")
    material.use_nodes = True
    shader = material.node_tree.nodes.get("Principled BSDF")
    if shader is not None:
        shader.inputs["Base Color"].default_value = (0.15, 0.45, 0.95, 1.0)
        if "Roughness" in shader.inputs:
            shader.inputs["Roughness"].default_value = 0.25
    return material


def _configure_output(contract: dict) -> str:
    output = contract.get("output", {})
    image = output.get("image")
    if not image:
        raise SystemExit("Scene contract has no output.image")

    # Relative paths are resolved against the working directory, which Java sets to the workspace root.
    absolute = os.path.abspath(image)
    os.makedirs(os.path.dirname(absolute), exist_ok=True)

    render = bpy.context.scene.render
    # Blender appends the extension itself, so hand it the path without one and get back exactly the
    # file Java is going to look for.
    render.filepath = os.path.splitext(absolute)[0]
    render.image_settings.file_format = "PNG"
    render.use_file_extension = True
    return os.path.splitext(absolute)[0] + ".png"


_OBJECT_BUILDERS = {
    "sphere": _add_sphere,
}


if __name__ == "__main__":
    main()
