#!/usr/bin/env python3

import argparse
import os

SOURCE_DIRS = [f.name for f in os.scandir("src") if f.is_dir()]


def move_package(src, dest):
    def _update(path):
        with open(path, "r") as f:
            contents = (
                f.read()
                .replace(f"package {src}", f"package {dest}")
                .replace(f"import {src}.", f"import {dest}.")
                .replace(f"import static {src}.", f"import static {dest}.")
            )
        with open(path, "w") as f:
            f.write(contents)

    print(f"Moving {src} to {dest}... ", end="", flush=True)
    os.system(f"cp -r src/{src} src/{dest}")

    for subdir, dirs, files in os.walk(f"src/{dest}"):
        for file in files:
            _update(os.path.join(subdir, file))
    print("Done.")


if __name__ == "__main__":

    parser = argparse.ArgumentParser(
        description="Move a package, renaming imports as necessary."
    )
    parser.add_argument(
        "source",
        help="Source directory to read from ",
        choices=SOURCE_DIRS,
    )
    parser.add_argument(
        "destination",
        help="Where to read to",
    )
    args = parser.parse_args()

    assert args.destination not in SOURCE_DIRS, "destination already exists!"

    move_package(args.source, args.destination)
