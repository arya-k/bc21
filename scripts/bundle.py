#!/usr/bin/env python3

import argparse
import os

SOURCE_DIRS = [f.name for f in os.scandir("src") if f.is_dir()]


def prepare_submit(src):
    def _update(path):
        with open(path, "r") as f:
            contents = (
                f.read()
                .replace(f"package {src}", f"package submit")
                .replace(f"import {src}.", f"import submit.")
                .replace(f"import static {src}.", f"import static submit.")
                .replace(f"System.out.print", f";// System.out.print")
                .replace(f"rc.resign", f";// rc.resign")
            )
        with open(path, "w") as f:
            f.write(contents)

    print(f"Moving {src} to submit/ ... ")
    os.system(f"cp -r src/{src} src/submit")

    for subdir, dirs, files in os.walk(f"src/submit"):
        for file in files:
            _update(os.path.join(subdir, file))

    print("Checking build... ")
    os.system("./gradlew build")

    print("Zipping...")
    os.system("zip -r submit.zip src/submit && rm -r src/submit")
    print("Done! Upload submit.zip")


if __name__ == "__main__":

    parser = argparse.ArgumentParser(
        description="Prepare a package for submission, commenting out all print statements."
    )
    parser.add_argument(
        "source",
        help="Bot name to bundle for submit.",
        choices=SOURCE_DIRS,
    )
    args = parser.parse_args()

    assert "submit" not in SOURCE_DIRS, "Error: This script expects there is no bot named submit!"

    prepare_submit(args.source)
