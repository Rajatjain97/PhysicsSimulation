"""Records what was rendered, beside the output.

Every successful render leaves demo.png and demo.json together. The manifest is what makes a render
reproducible later: it names the template, the shared materials that were used and the contract
version that produced the image, and carries the render id Java assigned, so a file on disk can
always be traced back to a run.
"""

import json
import os
from datetime import datetime, timezone
from typing import List

from .scene_contract import SceneContract


def write_manifest(image_path: str, render_id: str, contract: SceneContract, resolution: str,
                   materials: List[str]) -> str:
    """Writes <image>.json next to the image and returns its path."""
    manifest = {
        "renderId": render_id,
        "template": contract.template,
        "schemaVersion": contract.schema_version,
        "createdAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "resolution": resolution,
        "materials": list(materials),
        "output": {
            "image": os.path.basename(image_path),
        },
    }
    manifest_path = os.path.splitext(image_path)[0] + ".json"
    with open(manifest_path, "w", encoding="utf-8") as manifest_file:
        json.dump(manifest, manifest_file, indent=2)
        manifest_file.write("\n")
    return manifest_path
