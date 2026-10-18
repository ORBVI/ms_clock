"""Fail a tagged release if its version or release notes are inconsistent."""
import pathlib
import re
import sys

if len(sys.argv) != 2 or not re.fullmatch(r"v\d+\.\d+\.\d+", sys.argv[1]):
    raise SystemExit("Expected a release tag in vMAJOR.MINOR.PATCH form")
tag = sys.argv[1]
source = pathlib.Path("app/build.gradle").read_text()
version = re.search(r"versionName\s+['\"]([^'\"]+)['\"]", source)
if not version or version.group(1) != tag[1:]:
    raise SystemExit("Release tag does not match Android versionName")
if not pathlib.Path(f"docs/releases/{tag}.md").is_file():
    raise SystemExit("Missing release notes")
print(f"Release version verified: {tag}")
