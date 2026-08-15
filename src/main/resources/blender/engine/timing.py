"""How long each stage of a render took.

Added because the pipeline could not answer the only question that matters when a reel takes twenty
minutes: which part of it was slow. Every stage prints one line, so a render's own log is its profile
and no separate tooling is needed:

    timing.scene=0.42s
    timing.physics=3.10s
    timing.render=612.44s

Java measures the whole Blender process, so whatever it measures beyond the sum of these lines is
Blender's own startup and teardown - which is how startup cost gets measured without instrumenting
Blender itself.
"""

import time
from contextlib import contextmanager


@contextmanager
def measure(stage: str):
    """Times a stage and prints it, whether or not the stage succeeded."""
    started = time.perf_counter()
    try:
        yield
    finally:
        print("timing.{0}={1:.2f}s".format(stage, time.perf_counter() - started))
