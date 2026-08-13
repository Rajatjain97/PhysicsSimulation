"""The scene contract Java writes, parsed and validated on the Blender side.

Validation happens here, once, at the edge. Everything downstream can then assume the contract is
well formed, which is why no template ever has to check whether a field is present.
"""

import json
import os
from dataclasses import dataclass, field
from typing import Any, Dict

SUPPORTED_SCHEMA_VERSIONS = ("1.0",)


class SceneContractError(Exception):
    """The contract is missing, unreadable, or does not say what this engine understands."""


@dataclass(frozen=True)
class SceneContract:
    """What to render, where to put it, and how the template should be configured.

    The output is a single path. Whether it is a still or a movie is the template's decision - it
    declares a duration or it does not - so the contract only has to say which kind of file Java is
    expecting to find afterwards.
    """

    schema_version: str
    template: str
    output_path: str
    is_video: bool = False
    parameters: Dict[str, Any] = field(default_factory=dict)

    @staticmethod
    def load(path: str) -> "SceneContract":
        if not os.path.isfile(path):
            raise SceneContractError("Scene contract not found: " + path)
        try:
            with open(path, "r", encoding="utf-8") as contract_file:
                document = json.load(contract_file)
        except ValueError as error:
            raise SceneContractError("Scene contract is not valid JSON: " + str(error))

        schema_version = document.get("schemaVersion")
        if schema_version not in SUPPORTED_SCHEMA_VERSIONS:
            raise SceneContractError(
                "Unsupported schemaVersion {0}; this engine understands {1}".format(
                    schema_version, ", ".join(SUPPORTED_SCHEMA_VERSIONS)))

        template = document.get("template")
        if not template:
            raise SceneContractError("Scene contract has no template")

        output = document.get("output") or {}
        video_output = output.get("video")
        image_output = output.get("image")
        if not video_output and not image_output:
            raise SceneContractError("Scene contract has no output.video or output.image")

        parameters = document.get("parameters") or {}
        if not isinstance(parameters, dict):
            raise SceneContractError("Scene contract parameters must be an object")

        return SceneContract(schema_version, template, video_output or image_output,
                             bool(video_output), parameters)
