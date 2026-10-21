#!/usr/bin/env bash
# Uploads the plugin jar to its CurseForge project in the Bukkit Plugins section. mc-publish only
# knows the Mods section, so this talks to the upload API directly: Bukkit projects are served by
# dev.bukkit.org, with minecraft.curseforge.com tried as well in case the project answers there.
#
# Usage: curseforge-bukkit.sh <project id> <jar> <display name> <release type> <notes.md> <game versions...>
# Needs CURSEFORGE_TOKEN. Game versions the site doesn't list are tried as major.minor, then skipped.
# One name can exist in several version types and only some of them are valid for a Bukkit project,
# so every matching id is sent and the ones the site rejects are dropped one by one.
set -euo pipefail

project="$1"; jar="$2"; name="$3"; release_type="$4"; notes="$5"; shift 5
wanted=("$@")

for base in https://dev.bukkit.org https://minecraft.curseforge.com; do
  echo "Trying ${base}"
  status=$(curl -sS -o versions.json -w '%{http_code}' -H "X-Api-Token: ${CURSEFORGE_TOKEN}" "${base}/api/game/versions" || echo 000)
  if [ "${status}" != "200" ]; then
    echo "::warning::${base}: game versions answered ${status}"
    continue
  fi

  ids=()
  for version in "${wanted[@]}"; do
    found=$(jq -r --arg v "${version}" '.[] | select(.name == $v) | .id' versions.json)
    if [ -z "${found}" ]; then
      short=$(cut -d. -f1-2 <<< "${version}")
      found=$(jq -r --arg v "${short}" '.[] | select(.name == $v) | .id' versions.json)
    fi
    if [ -n "${found}" ]; then
      mapfile -t -O "${#ids[@]}" ids <<< "${found}"
    else
      echo "::warning::${base} has no game version ${version}"
    fi
  done
  if [ "${#ids[@]}" -eq 0 ]; then
    echo "::warning::${base}: none of the game versions matched; the site lists:"
    jq -r '.[].name' versions.json | sort -uV | tail -n 60 | tr '\n' ' '
    echo
    continue
  fi

  mapfile -t ids < <(printf '%s\n' "${ids[@]}" | sort -un)
  echo "Matched (id:type:name):" \
    "$(jq -r --argjson ids "$(printf '%s\n' "${ids[@]}" | jq -s .)" '[.[] | select(.id as $i | $ids | index($i)) | "\(.id):\(.gameVersionTypeID):\(.name)"] | join(" ")' versions.json)"
  for attempt in $(seq 1 100); do
    jq -n \
      --rawfile changelog "${notes}" \
      --arg name "${name}" \
      --arg type "${release_type}" \
      --argjson versions "$(printf '%s\n' "${ids[@]}" | jq -s .)" \
      '{changelog: $changelog, changelogType: "markdown", displayName: $name, gameVersions: $versions, releaseType: $type}' \
      > metadata.json

    status=$(curl -sS -o upload.json -w '%{http_code}' -H "X-Api-Token: ${CURSEFORGE_TOKEN}" \
      -F "metadata=<metadata.json" -F "file=@${jar}" \
      "${base}/api/projects/${project}/upload-file" || echo 000)
    if [ "${status}" = "200" ]; then
      echo "Uploaded to ${base}: file $(jq -r '.id' upload.json), game versions:" \
        "$(jq -r --argjson ids "$(printf '%s\n' "${ids[@]}" | jq -s .)" '[.[] | select(.id as $i | $ids | index($i)) | .name] | unique | join(" ")' versions.json)"
      exit 0
    fi
    echo "Upload answered ${status}: $(head -c 600 upload.json 2>/dev/null)"
    # The body is not always a JSON object (an error page, a bare string): read it defensively
    rejected=$( (jq -r 'try (.errorMessage // "") catch ""' upload.json 2>/dev/null || true) | sed -n 's/^Invalid game version ID: \([0-9]*\).*/\1/p')
    if [ "${status}" != "400" ] || [ -z "${rejected}" ]; then
      break
    fi
    mapfile -t ids < <(printf '%s\n' "${ids[@]}" | grep -vx "${rejected}" || true)
    if [ "${#ids[@]}" -eq 0 ] || [ -z "${ids[0]}" ]; then
      break
    fi
  done
  echo "::warning::${base}: upload answered ${status}: $(cat upload.json 2>/dev/null)"
  echo "Version types there:"
  curl -sS -H "X-Api-Token: ${CURSEFORGE_TOKEN}" "${base}/api/game/version-types" | jq -c '.[] | {id, name, slug}' | head -n 40 || true
done

echo "::error::The plugin could not be uploaded to CurseForge project ${project}"
exit 1
