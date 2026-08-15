"""Every random decision in a reel, and where it comes from.

One number - the render seed - decides every random choice a template makes. Give the same seed to
the same template with the same parameters and you get the same scene, every time, on any machine.
That is what makes a library of thousands of reels reproducible: a manifest is enough to rebuild the
one you liked.

Templates never touch Python's global random module. They ask the context for a named stream:

    placement = context.random.stream("placement")
    colours = context.random.stream("colour")

Named streams are the reason this is worth having. Each name gets its own generator, derived from the
render seed and the name, so adding a random decision to the camera cannot shift every marble on the
floor. Streams are created once and cached; drawing from one is as cheap as calling random directly.

Deliberately not used here: Python's hash(). It is salted per process, so it would make the same seed
produce different scenes in different runs - the exact bug this module exists to prevent.
"""

import random
import zlib
from typing import Any, Dict, Optional, Sequence

SEED_PARAMETER = "seed"


class SeedError(Exception):
    """The seed is not a whole number."""


class RandomStream:
    """One named source of randomness. A thin, deliberate surface over random.Random."""

    def __init__(self, seed: int):
        self._random = random.Random(seed)
        self.seed = seed

    def random(self) -> float:
        """A float in [0, 1)."""
        return self._random.random()

    def randint(self, low: int, high: int) -> int:
        """A whole number in [low, high], both ends included."""
        return self._random.randint(low, high)

    def uniform(self, low: float, high: float) -> float:
        return self._random.uniform(low, high)

    def choice(self, population: Sequence):
        return self._random.choice(population)

    def shuffle(self, items: list) -> None:
        """Shuffles in place, like random.shuffle."""
        self._random.shuffle(items)

    def sample(self, population: Sequence, count: int) -> list:
        return self._random.sample(population, count)


class RandomContext:
    """The render's randomness: one seed, and a named stream for each area that needs it."""

    def __init__(self, seed: int):
        self.seed = int(seed)
        self._streams: Dict[str, RandomStream] = {}

    @staticmethod
    def from_parameters(parameters: Dict[str, Any], fallback: Optional[int] = None) -> "RandomContext":
        """Reads the seed a render was given.

        Java supplies one on every render - the operator's if they set it, a generated one otherwise -
        so the fallback is only for a contract written by hand.
        """
        raw = parameters.get(SEED_PARAMETER, fallback)
        if raw is None:
            raise SeedError("No seed in the scene contract, and no fallback was offered")
        try:
            return RandomContext(int(raw))
        except (TypeError, ValueError):
            raise SeedError("Seed must be a whole number but was {0!r}".format(raw))

    def stream(self, name: str) -> RandomStream:
        """The generator for one named area of the scene, created once and reused."""
        if not name:
            raise SeedError("A random stream needs a name")
        stream = self._streams.get(name)
        if stream is None:
            stream = RandomStream(self._derive(name))
            self._streams[name] = stream
        return stream

    def stream_names(self) -> list:
        return sorted(self._streams)

    def _derive(self, name: str) -> int:
        """A child seed from the render seed and the stream name.

        CRC32 because it is stable across processes, platforms and Python versions - which is the
        whole requirement. It is not a cryptographic hash and does not need to be.
        """
        return (self.seed ^ zlib.crc32(name.encode("utf-8"))) & 0xFFFFFFFF
