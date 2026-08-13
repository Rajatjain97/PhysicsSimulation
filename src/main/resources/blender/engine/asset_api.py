"""The interface every shared asset implements.

An asset definition declares what an asset *is*; the repository that owns it decides when to create
it and how long it lives. Templates see neither - they ask for an identifier and get something they
can use.

Materials are the only asset category implemented today. Environments, fonts, textures, models and
audio each get their own definition base and their own repository when the story that needs them
arrives, rather than being bolted onto this one.
"""

import abc


class MaterialDefinition(abc.ABC):
    """How one shared material is built.

    Implementations live in blender/assets/materials and expose a module level MATERIAL instance.
    They set shader inputs and material flags; they never create, name or cache the material itself,
    because that is the repository's job and it is what keeps identifiers stable.
    """

    #: Stable, human readable identifier used in templates and parameters, e.g. "DefaultGlass".
    name = ""

    #: One line describing the look, surfaced by the repository.
    description = ""

    @abc.abstractmethod
    def configure(self, material, shader) -> None:
        """Configures an already created Blender material.

        :param material: the bpy material, for material level flags
        :param shader:   its Principled BSDF node, or None if the node could not be found
        """


def set_shader_input(shader, names, value) -> None:
    """Sets the first shader input that exists.

    Principled BSDF input names move between Blender versions - 'Transmission' became 'Transmission
    Weight' in 4.0 - so an asset asks for the first name that is actually there instead of pinning
    itself to one Blender release.
    """
    if shader is None:
        return
    for name in names:
        if name in shader.inputs:
            shader.inputs[name].default_value = value
            return


def set_material_flag(material, name: str, value) -> None:
    """Sets a material level flag when this Blender version has it.

    EEVEE specific switches come and go between releases; Cycles ignores them entirely.
    """
    if hasattr(material, name):
        setattr(material, name, value)
