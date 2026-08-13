"""DefaultMetal - brushed steel for opponents, rails and obstacles.

Visually the opposite of DefaultGlass on purpose: it reflects the studio lights instead of
transmitting them, so the two read as clearly different objects in the same shot. Roughness is high
enough to catch soft highlights rather than mirroring the whole room.
"""

from engine.asset_api import MaterialDefinition, set_shader_input


class DefaultMetal(MaterialDefinition):

    name = "DefaultMetal"
    description = "Brushed steel with a clean specular response."

    def configure(self, material, shader) -> None:
        set_shader_input(shader, ("Base Color",), (0.72, 0.74, 0.78, 1.0))
        set_shader_input(shader, ("Metallic",), 1.0)
        set_shader_input(shader, ("Roughness",), 0.22)
        set_shader_input(shader, ("Transmission Weight", "Transmission"), 0.0)
        set_shader_input(shader, ("IOR",), 2.5)


MATERIAL = DefaultMetal()
