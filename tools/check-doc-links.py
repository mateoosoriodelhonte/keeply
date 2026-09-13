#!/usr/bin/env python3
"""Checks that every relative link in the documentation points at something.

Keeply's documentation is part of the product: a README that promises a file
which is not there is a broken promise, not a typo. This runs in CI so a moved
file cannot quietly leave a dead link behind.

External links are not followed, deliberately: a check that depends on somebody
else's website stays green only as long as they do.
"""

import pathlib
import re
import sys

LINK = re.compile(r"\[([^\]]*)\]\(([^)]+)\)")
EXTERNAL = ("http://", "https://", "mailto:", "#")


def main() -> int:
    root = pathlib.Path(__file__).resolve().parent.parent
    broken: list[str] = []
    checked = 0

    for markdown in sorted(root.glob("*.md")) + sorted(root.glob("docs/**/*.md")):
        for text, target in LINK.findall(markdown.read_text()):
            if target.startswith(EXTERNAL):
                continue
            checked += 1
            path = (markdown.parent / target.split("#")[0]).resolve()
            if not path.exists():
                broken.append(f"{markdown.relative_to(root)}: [{text}]({target})")

    if broken:
        print(f"{len(broken)} broken link(s):")
        for entry in broken:
            print(f"  {entry}")
        return 1

    print(f"All {checked} relative documentation links resolve.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
