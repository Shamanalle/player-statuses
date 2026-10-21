#!/usr/bin/env bash
# Runs a command, and runs it once more when it failed on a download before any check began.
#
#   net-retry.sh [--started <regex>] -- <command> [args...]
#
# Only a network failure (net-failure.sh) is tried again, and only when no output line matched
# --started: the line that shows the tests themselves began (the task that starts the game, say).
# A failing test, a compile error, a missing file and a second failure are the job's result.
# The second try fetches Maven Central's files from Google's mirror of it (maven-mirror.init.gradle),
# for every Gradle build the command starts, the separate legacy builds too.
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=net-failure.sh
. "${here}/net-failure.sh"

started=''
while [ $# -gt 0 ]; do
  case "$1" in
    --started) started="$2"; shift 2 ;;
    --) shift; break ;;
    *) echo "net-retry.sh: unknown option $1" >&2; exit 2 ;;
  esac
done
[ $# -gt 0 ] || { echo "net-retry.sh: no command" >&2; exit 2; }

log="$(mktemp)"
"$@" 2>&1 | tee "${log}"
status=${PIPESTATUS[0]}
if [ "${status}" -eq 0 ]; then
  exit 0
fi
if [ -n "${started}" ] && grep -Eq "${started}" "${log}"; then
  exit "${status}"
fi
if ! network_failure "${log}"; then
  exit "${status}"
fi

reason=$(grep -Eo "${net_failure_pattern}" "${log}" | head -1)
echo "::warning title=Network failure::${reason}. Trying once more, Maven Central through its Google mirror"
init_dir="${GRADLE_USER_HOME:-${HOME}/.gradle}/init.d"
mirror="${init_dir}/playerstatuses-maven-mirror.gradle"
mkdir -p "${init_dir}"
cp "${here}/maven-mirror.init.gradle" "${mirror}"
trap 'rm -f "${mirror}"' EXIT
sleep "${NET_RETRY_DELAY:-30}"
"$@"
