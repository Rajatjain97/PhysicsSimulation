"""DefaultGlass - clear glass for marbles, spheres and maze walls.

Tuned for the look these reels need: fully transmissive, almost no surface roughness so highlights
stay crisp, and an IOR in the range of real glass so refraction reads correctly at close camera
distances.

Needs enough transmission bounces in the render settings, or glass renders black. Templates set that
through their render settings; it is a scene concern, not a material one.
"""

from engine.asset_api import MaterialDefinition, set_material_flag, set_shader_input


class DefaultGlass(MaterialDefinition):

    name = "DefaultGlass"
    description = "Clear glass with physically plausible refraction and clean highlights."

    def configure(self, material, shader) -> None:
        set_shader_input(shader, ("Base Color",), (1.0, 1.0, 1.0, 1.0))
        set_shader_input(shader, ("Transmission Weight", "Transmission"), 1.0)
        set_shader_input(shader, ("Roughness",), 0.02)
        set_shader_input(shader, ("IOR",), 1.45)
        set_shader_input(shader, ("Metallic",), 0.0)
        set_shader_input(shader, ("Alpha",), 1.0)

        # EEVEE only; Cycles refracts regardless. Applied through the helper because these switches
        # move between Blender releases.
        set_material_flag(material, "use_screen_refraction", True)
        set_material_flag(material, "use_backface_culling", False)


MATERIAL = DefaultGlass()
