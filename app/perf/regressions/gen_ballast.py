"""R4: writes a 2 MiB incompressible asset (seeded, deterministic) into the Android app; `revert` deletes it."""

import os
import random
import sys

SIZE = 2 * 1024 * 1024


def main(repo_root: str) -> str:
    path = os.path.join(repo_root, "app", "androidApp", "src", "main", "assets", "tracepulse-ballast.bin")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    rng = random.Random(7)
    with open(path, "wb") as fh:
        fh.write(bytes(rng.getrandbits(8) for _ in range(SIZE)))
    return path


if __name__ == "__main__":
    print(main(sys.argv[1] if len(sys.argv) > 1 else os.getcwd()))
