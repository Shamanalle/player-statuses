#!/usr/bin/env python3
"""Checks that every file of a release is in the stores with all its Minecraft versions and loaders.

Usage: check-stores.py <tag> [--warn-only]

The expected files come from the publish matrix in .github/workflows/publish.yml, so the two cannot
drift apart. Modrinth is read from its public API; CurseForge from api.cfwidget.com, which mirrors
the public file list (the CurseForge API itself needs a key we do not have, and the upload token
cannot list files). cfwidget refreshes a project every few minutes, so a check right after an
upload can miss the newest files.

A Markdown table goes to $GITHUB_STEP_SUMMARY; every gap is reported as an error (a warning with
--warn-only) and makes the script exit 1.
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request

import yaml

CF_LOADERS = {"fabric": "Fabric", "quilt": "Quilt", "forge": "Forge", "neoforge": "NeoForge"}


def get_json(url, token=None, attempts=6):
    """GETs a JSON document; cfwidget answers 202 while it fetches a project for the first time."""
    headers = {"User-Agent": "Shamanalle/player-statuses store check"}
    if token:
        headers["Authorization"] = token
    for attempt in range(attempts):
        request = urllib.request.Request(url, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                if response.status == 200:
                    return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code not in (202, 429, 500, 502, 503, 504):
                raise RuntimeError(f"{url}: HTTP {error.code}") from None
        time.sleep(10 * (attempt + 1))
    raise RuntimeError(f"No answer from {url}")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    warn_only = "--warn-only" in sys.argv
    if len(args) != 1:
        sys.exit(__doc__)
    tag = args[0]
    version = tag.removeprefix("v")

    with open(".github/publish.json") as f:
        ids = json.load(f)
    with open(".github/workflows/publish.yml") as f:
        rows = yaml.safe_load(f)["jobs"]["publish"]["strategy"]["matrix"]["include"]

    # With the token, as a project still in review is not public
    modrinth = get_json(f"https://api.modrinth.com/v2/project/{ids['modrinth']}/version",
                        os.environ.get("MODRINTH_TOKEN"))
    modrinth = {v["version_number"]: v for v in modrinth}
    curseforge = {}
    for key in ("curseforge", "curseforge_plugin"):
        if ids.get(key):
            files = get_json(f"https://api.cfwidget.com/{ids[key]}").get("files", [])
            curseforge[key] = {f.get("name", ""): f for f in files} | {f.get("display", ""): f for f in files}

    lines = ["| File | Store | Result |", "| --- | --- | --- |"]
    gaps = []

    def report(row, store, missing):
        if missing:
            gaps.append(f"{store}: {row['name']}: {missing}")
        lines.append(f"| {row['name']} | {store} | {missing or 'ok'} |")

    for row in rows:
        game_versions = row["game-versions"].split()
        loaders = row["loaders"].split()

        # Modrinth: the version number is {version}+{target}
        found = modrinth.get(f"{version}+{row['target']}")
        if found is None:
            report(row, "Modrinth", "not uploaded")
        else:
            problems = []
            missing = [v for v in game_versions if v not in found["game_versions"]]
            if missing:
                problems.append("versions " + " ".join(missing))
            missing = [l for l in loaders if l not in found["loaders"]]
            if missing:
                problems.append("loaders " + " ".join(missing))
            listed = ", ".join(found["game_versions"] + found["loaders"])
            report(row, "Modrinth", "; ".join(problems) + (f" (listed: {listed})" if problems else ""))

        # CurseForge: the mod rows go to the mod project, the plugin to its own project
        key = "curseforge_plugin" if row["target"] == "bukkit" else "curseforge"
        if key not in curseforge:
            continue
        # By file name: display names changed over time ("Mod for …" in 2.6.0). The stores get the
        # Fabric jar without "fabric" in its name, as publish.yml uploads it.
        file_name = "player-statuses-" + row["file"].replace("{v}", version)
        file_name = file_name.replace("-fabric-", "-")
        found = curseforge[key].get(file_name) or curseforge[key].get(f"{version} · {row['name']}")
        if found is None:
            report(row, "CurseForge", "not uploaded")
            continue
        listed = found.get("versions", [])
        problems = []
        # The plugin section lacks most patch versions: curseforge-bukkit.sh falls back to major.minor.
        # Its 1.16 entry is broken ("game version id: 0 does not exist"), so 1.16.5 cannot be listed.
        missing = [v for v in game_versions if v not in listed
                   and not (key == "curseforge_plugin" and (".".join(v.split(".")[:2]) in listed or v.startswith("1.16")))]
        if missing:
            problems.append("versions " + " ".join(missing))
        if key == "curseforge":
            missing = [CF_LOADERS[l] for l in loaders if CF_LOADERS[l] not in listed]
            if missing:
                problems.append("loaders " + " ".join(missing))
        # What the store has helps to see why a version did not match
        report(row, "CurseForge", "; ".join(problems) + (f" (listed: {', '.join(listed)})" if problems else ""))

    table = "\n".join([f"### Store check for {tag}", ""] + lines) + "\n"
    print(table)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(table)
    level = "warning" if warn_only else "error"
    for gap in gaps:
        print(f"::{level}::{gap}")
    sys.exit(1 if gaps and not warn_only else 0)


if __name__ == "__main__":
    main()
