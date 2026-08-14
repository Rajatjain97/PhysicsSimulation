"""Owns shared materials: discovery, resolution and lifetime.

Discovery is by convention, exactly like templates: blender/assets/materials/<anything>.py declaring
a module level MATERIAL. Adding DefaultPlastic is one file - no registration list, no if/else chain,
no change to this module.

Lifetime is deliberately simple and local to one render. A material is created the first time it is
asked for and reused afterwards, and an identical material already present in the file is adopted
rather than duplicated. There is no cross-render cache; renders are separate Blender processes, and
inventing one now would buy nothing.
"""

import importlib.util
import os
from typing import Dict, List

import bpy

from .asset_api import MaterialDefinition, set_shader_input

MATERIAL_ATTRIBUTE = "MATERIAL"


class AssetError(Exception):
    """Base class for asset resolution problems."""


class UnknownMaterialError(AssetError):
    """A template asked for a material that is not installed."""


class InvalidMaterialError(AssetError):
    """A material module exists but does not satisfy the interface."""


class MaterialRepository:
    """Resolves material identifiers to Blender materials."""

    def __init__(self, materials_root: str):
        self._materials_root = materials_root
        self._definitions_by_name = None
        self._resolved = {}

    def names(self) -> List[str]:
        """Every installed material identifier, sorted."""
        return sorted(self._definitions().keys())

    def resolved_names(self) -> List[str]:
        """Identifiers actually used by this render, sorted. Recorded in the render manifest."""
        return sorted(self._resolved.keys())

    def resolve(self, identifier: str):
        """Returns the Blender material for an identifier, creating it on first use.

        Fails loudly on an unknown identifier: a silent fallback would produce a render that looks
        wrong without anything looking broken.
        """
        definition = self._definitions().get(identifier)
        if definition is None:
            raise UnknownMaterialError(
                'Unknown material: "{0}". Installed materials: {1}'.format(
                    identifier, ", ".join(self.names()) or "(none)"))

        cached = self._resolved.get(identifier)
        if cached is not None and _is_alive(cached):
            return cached

        # A template may reset the scene mid-build, which invalidates anything created earlier, and a
        # .blend template may already carry the material. Both are handled by looking it up by name.
        material = bpy.data.materials.get(identifier)
        if material is None:
            # The identifier is the Blender name on purpose: stable, readable, and never a generated
            # id. Creating it only when absent is what stops Blender appending ".001".
            material = bpy.data.materials.new(identifier)
            material.use_nodes = True
            definition.configure(material, _principled_shader(material))

        self._resolved[identifier] = material
        return material

    def resolve_variant(self, identifier: str, variant: str, tint):
        """Returns a coloured copy of a shared material, created once and reused.

        A scene full of glass marbles wants one glass definition and a handful of colours, not one
        material definition per colour. The variant keeps the base material's look - its refraction,
        roughness and IOR all come from the shared definition - and changes only the tint, so a
        marble is unmistakably DefaultGlass in a particular colour.
        """
        key = "{0}:{1}".format(identifier, variant)
        cached = self._resolved.get(key)
        if cached is not None and _is_alive(cached):
            return cached

        material = bpy.data.materials.get(key)
        if material is None:
            material = self.resolve(identifier).copy()
            material.name = key
            set_shader_input(_principled_shader(material), ("Base Color",),
                             (float(tint[0]), float(tint[1]), float(tint[2]), 1.0))

        self._resolved[key] = material
        return material

    def _definitions(self) -> Dict[str, MaterialDefinition]:
        if self._definitions_by_name is not None:
            return self._definitions_by_name
        if not os.path.isdir(self._materials_root):
            raise AssetError("Materials directory not found: " + self._materials_root)

        definitions = {}
        for entry in sorted(os.listdir(self._materials_root)):
            if not entry.endswith(".py") or entry.startswith("_"):
                continue
            module_path = os.path.join(self._materials_root, entry)
            definition = self._load(entry, module_path)
            if definition.name in definitions:
                raise InvalidMaterialError(
                    "Two materials declare the name '{0}' in {1}".format(definition.name, self._materials_root))
            definitions[definition.name] = definition
        self._definitions_by_name = definitions
        return definitions

    @staticmethod
    def _load(entry: str, module_path: str) -> MaterialDefinition:
        module_name = "physics_reel_material_" + os.path.splitext(entry)[0]
        specification = importlib.util.spec_from_file_location(module_name, module_path)
        module = importlib.util.module_from_spec(specification)
        specification.loader.exec_module(module)

        definition = getattr(module, MATERIAL_ATTRIBUTE, None)
        if definition is None:
            raise InvalidMaterialError(module_path + " must define " + MATERIAL_ATTRIBUTE)
        if not isinstance(definition, MaterialDefinition):
            raise InvalidMaterialError(
                module_path + " must set " + MATERIAL_ATTRIBUTE + " to a MaterialDefinition instance")
        if not getattr(definition, "name", ""):
            raise InvalidMaterialError(module_path + " must give its material a name")
        return definition


def _principled_shader(material):
    return material.node_tree.nodes.get("Principled BSDF")


def _is_alive(material) -> bool:
    """A Blender datablock wrapper goes stale when the file is reset underneath it."""
    try:
        return material.name is not None
    except ReferenceError:
        return False
