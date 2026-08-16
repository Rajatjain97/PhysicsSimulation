"""Records what was rendered, beside the output.

Every successful render leaves the output and its manifest together. The manifest is what makes a
render reproducible later: the template, the parameters it was given, the shared materials it used
and the contract version that produced it, plus the render id Java assigned, so a file on disk can
always be traced back to a run.
"""

import json
import os
from datetime import datetime, timezone
from typing import List, Optional

from .renderer import RenderOutcome
from .scene_contract import SceneContract
from .template_api import DurationPlan


def write_manifest(outcome: RenderOutcome, render_id: str, contract: SceneContract,
                   materials: List[str], plan: Optional[DurationPlan] = None) -> str:
    """Writes <output>.json next to the output file and returns its path.

    The duration fields exist so the length of a reel is explainable after the fact: what was asked
    for, what the content needed, what was held afterwards, and what was produced.
    """
    manifest = {
        "renderId": render_id,
        "template": contract.template,
        "schemaVersion": contract.schema_version,
        "createdAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "resolution": outcome.resolution,
        "fps": outcome.fps,
        "durationSeconds": outcome.duration_seconds,
        "frames": outcome.frames,
        "parameters": dict(contract.parameters),
        "materials": list(materials),
        "output": {
            "video" if contract.is_video else "image": os.path.basename(outcome.output_path),
        },
    }
    if plan is not None:
        manifest.update({
            "requestedDurationSeconds": round(plan.requested_seconds, 3),
            "contentDurationSeconds": plan.content_seconds,
            "postEventHoldSeconds": plan.hold_seconds,
            "contentSettled": plan.settled,
        })

    manifest_path = os.path.splitext(outcome.output_path)[0] + ".json"
    with open(manifest_path, "w", encoding="utf-8") as manifest_file:
        json.dump(manifest, manifest_file, indent=2)
        manifest_file.write("\n")
    return manifest_path
