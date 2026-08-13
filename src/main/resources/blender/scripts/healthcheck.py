"""Physics Reel Studio - Blender integration healthcheck.

Run by Java as:

    blender --background --python healthcheck.py

It proves one thing only: Java can start Blender, Blender can run our Python, and the process ends
cleanly. It renders nothing, opens nothing and writes nothing - keep it that way, so a failure here
always means the integration is broken rather than a scene being wrong.

The key=value output format is deliberate: it stays readable in logs and is trivial to assert on.
"""

import os
import sys

import bpy


def main() -> None:
    print("blender.version=" + bpy.app.version_string)
    print("python.version=" + sys.version.split()[0])
    print("working.directory=" + os.getcwd())


if __name__ == "__main__":
    main()
