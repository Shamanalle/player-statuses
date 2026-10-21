#!/usr/bin/env bash
# Prints the GitHub release notes of one version: its CHANGELOG.md section (English, then Russian)
# followed by a pointer to the file table in the README.
# Usage: release-notes.sh <version or tag>, e.g. 1.2.0 or v1.2.0. Fails when the section is missing.
set -euo pipefail

version="${1#v}"
changelog="$(dirname "$0")/../../CHANGELOG.md"

section="$(awk -v v="$version" '
  index($0, "## [" v "]") == 1 { found = 1; next }
  found && /^## \[/ { exit }
  found { print }
' "$changelog")"

if [ -z "$(printf '%s' "$section" | tr -d '[:space:]')" ]; then
  echo "CHANGELOG.md has no section for ${version}" >&2
  exit 1
fi

printf '%s\n\n---\n\n' "$section"
cat <<'NOTES'
**Which file do I need?** See [Versions and files](https://github.com/Shamanalle/player-statuses#versions-and-files) in the README.

**Какой файл нужен?** См. раздел [Версии и файлы](https://github.com/Shamanalle/player-statuses/blob/main/README.ru.md#версии-и-файлы) в README.
NOTES
