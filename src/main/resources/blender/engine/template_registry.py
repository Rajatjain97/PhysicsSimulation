"""Discovery, validation and resolution of templates.

A template is a directory containing template.py, and that module declares a TEMPLATE instance. The
registry scans for those, keys them by the name the module declares - not by its directory - so a
template can be renamed on disk without breaking any contract that references it.

Discovery is by convention, so adding MarbleArena is:

    blender/templates/marble_arena/template.py

and nothing else. No registration list, no if/else chain, no engine change.
"""

import importlib.util
import os
from dataclasses import dataclass
from typing import Dict, List

from .template_api import Template

TEMPLATE_MODULE = "template.py"
TEMPLATE_ATTRIBUTE = "TEMPLATE"


class TemplateError(Exception):
    """Base class for template resolution problems."""


class TemplateNotFoundError(TemplateError):
    """No template with the requested name is installed."""


class InvalidTemplateError(TemplateError):
    """A template module exists but does not satisfy the interface."""


@dataclass(frozen=True)
class TemplateDescriptor:
    """What the renderer needs to know about a template: who it is and how to build it."""

    name: str
    description: str
    directory: str
    module_path: str
    template: Template


class TemplateRegistry:

    def __init__(self, templates_root: str):
        self._templates_root = templates_root
        self._descriptors = None

    def names(self) -> List[str]:
        """Every installed template name, sorted."""
        return sorted(self._discover().keys())

    def resolve(self, name: str) -> TemplateDescriptor:
        descriptors = self._discover()
        descriptor = descriptors.get(name)
        if descriptor is None:
            raise TemplateNotFoundError(
                "Unknown template '{0}'. Installed templates: {1}".format(
                    name, ", ".join(sorted(descriptors)) or "(none)"))
        return descriptor

    def _discover(self) -> Dict[str, TemplateDescriptor]:
        if self._descriptors is not None:
            return self._descriptors
        if not os.path.isdir(self._templates_root):
            raise TemplateError("Templates directory not found: " + self._templates_root)

        descriptors = {}
        for entry in sorted(os.listdir(self._templates_root)):
            directory = os.path.join(self._templates_root, entry)
            module_path = os.path.join(directory, TEMPLATE_MODULE)
            if not os.path.isfile(module_path):
                continue
            descriptor = self._load(entry, directory, module_path)
            if descriptor.name in descriptors:
                raise InvalidTemplateError(
                    "Two templates declare the name '{0}': {1} and {2}".format(
                        descriptor.name, descriptors[descriptor.name].directory, directory))
            descriptors[descriptor.name] = descriptor
        self._descriptors = descriptors
        return descriptors

    @staticmethod
    def _load(entry: str, directory: str, module_path: str) -> TemplateDescriptor:
        specification = importlib.util.spec_from_file_location("physics_reel_template_" + entry, module_path)
        module = importlib.util.module_from_spec(specification)
        specification.loader.exec_module(module)

        template = getattr(module, TEMPLATE_ATTRIBUTE, None)
        if template is None:
            raise InvalidTemplateError(module_path + " must define " + TEMPLATE_ATTRIBUTE)
        if not isinstance(template, Template):
            raise InvalidTemplateError(module_path + " must set " + TEMPLATE_ATTRIBUTE + " to a Template instance")
        if not getattr(template, "name", ""):
            raise InvalidTemplateError(module_path + " must give its template a name")

        return TemplateDescriptor(
            name=template.name,
            description=getattr(template, "description", ""),
            directory=directory,
            module_path=module_path,
            template=template,
        )
