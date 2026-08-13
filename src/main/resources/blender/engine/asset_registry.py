"""The single handle templates use to reach shared assets.

Deliberately thin. It owns no asset logic of its own - it hands out focused repositories, so this
never becomes the class with getMaterial(), getFont(), getTexture() and getSound() on it. When
environments or fonts are needed, each arrives as its own repository exposed here, and everything
that already exists keeps working.
"""

import os

from .material_repository import MaterialRepository

MATERIALS_DIRECTORY = "materials"


class AssetRegistry:

    def __init__(self, assets_root: str):
        self._materials = MaterialRepository(os.path.join(assets_root, MATERIALS_DIRECTORY))

    @property
    def materials(self) -> MaterialRepository:
        return self._materials
