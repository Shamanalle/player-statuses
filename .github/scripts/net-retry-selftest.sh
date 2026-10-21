#!/usr/bin/env bash
# Self-test for net-failure.sh and net-retry.sh: which failures count as network ones, and that only
# those are tried again, once, and never after the checks began.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
. "${here}/net-failure.sh"
work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT
failed=0

check() {
  local want="$1" text="$2"
  printf '%s\n' "${text}" > "${work}/log"
  if network_failure "${work}/log"; then got=network; else got=other; fi
  if [ "${got}" != "${want}" ]; then
    echo "FAIL: expected ${want}, got ${got}: ${text}"
    failed=1
  fi
}

# Seen in this repository's runs or in the tools' own messages
check network "These libraries failed to download. Try again."
check network "> Could not GET 'https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.pom'. Received status code 429 from server: Too Many Requests"
check network "> Could not HEAD 'https://maven.fabricmc.net/net/fabricmc/yarn/maven-metadata.xml'. Received status code 502 from server: Bad Gateway"
check network "java.net.SocketTimeoutException: Read timed out"
check network "Caused by: java.net.UnknownHostException: piston-data.mojang.com"
check network "javax.net.ssl.SSLHandshakeException: Remote host terminated the handshake"
check network "java.io.IOException: Server returned HTTP response code: 503 for URL: https://piston-data.mojang.com/v1/objects/x/server.jar"
check network "curl: (22) The requested URL returned error: 429"
check network "curl: (6) Could not resolve host: api.modrinth.com"
check network "java.net.SocketException: Connection reset"
# Not network failures: trying again cannot help, or the code is wrong
check other "> Could not find java-objc-bridge-1.1.jar (ca.weblite:java-objc-bridge:1.1)."
check other "curl: (22) The requested URL returned error: 404"
check other "> Could not GET 'https://maven.neoforged.net/x.pom'. Received status code 404 from server: Not Found"
check other "error: cannot find symbol"
check other "VoiceFilterTest > glides() FAILED"
check other "java.lang.NoSuchMethodError: 'void net.minecraft.server.MinecraftServer.tick()'"
check other ""

# net-retry.sh: a fake command that prints a line and fails, counting its runs
export NET_RETRY_DELAY=0
export GRADLE_USER_HOME="${work}/gradle-home"
cat > "${work}/cmd.sh" <<'SH'
#!/usr/bin/env bash
n=$(( $(cat "$1" 2>/dev/null || echo 0) + 1 )); echo "${n}" > "$1"
[ -f "${GRADLE_USER_HOME}/init.d/playerstatuses-maven-mirror.gradle" ] && echo "mirror on"
if [ "${n}" -ge "${3:-99}" ]; then echo "ok on run ${n}"; exit 0; fi
printf '%b\n' "$2"; exit 7
SH
chmod +x "${work}/cmd.sh"

retry() {
  local want_runs="$1" want_status="$2"; shift 2
  rm -f "${work}/runs"
  set +e
  bash "${here}/net-retry.sh" "$@" > "${work}/out" 2>&1
  local status=$?
  set -e
  local runs; runs=$(cat "${work}/runs")
  if [ "${runs}" != "${want_runs}" ] || [ "${status}" != "${want_status}" ]; then
    echo "FAIL: net-retry.sh $*: ${runs} runs, status ${status} (expected ${want_runs} runs, status ${want_status})"
    sed 's/^/    /' "${work}/out"
    failed=1
  fi
}
net="> Could not GET 'https://repo.maven.apache.org/x.pom'. Received status code 503 from server"

# A network failure is tried once more; the second result counts, failure or success
retry 2 7 -- "${work}/cmd.sh" "${work}/runs" "${net}"
retry 2 0 -- "${work}/cmd.sh" "${work}/runs" "${net}" 2
grep -q "mirror on" "${work}/out" || { echo "FAIL: the second try did not get the Maven Central mirror"; failed=1; }
grep -q "::warning title=Network failure::" "${work}/out" || { echo "FAIL: no warning on the run for the second try"; failed=1; }
[ ! -e "${GRADLE_USER_HOME}/init.d/playerstatuses-maven-mirror.gradle" ] || { echo "FAIL: the mirror stayed in init.d"; failed=1; }
# Anything else is not tried again
retry 1 7 -- "${work}/cmd.sh" "${work}/runs" "error: cannot find symbol"
retry 1 7 -- "${work}/cmd.sh" "${work}/runs" "Could not find core-3.6.7.jar"
retry 1 0 -- "${work}/cmd.sh" "${work}/runs" "${net}" 1
# Nor after the checks began, even when the output then names a network failure
retry 1 7 --started '^> Task :fabric-1\.21:runClientGameTest' -- "${work}/cmd.sh" "${work}/runs" "> Task :fabric-1.21:runClientGameTest\n${net}"
retry 2 7 --started '^> Task :fabric-1\.21:runClientGameTest' -- "${work}/cmd.sh" "${work}/runs" "> Task :fabric-1.21:compileJava\n${net}"

if [ "${failed}" != 0 ]; then
  exit 1
fi
echo "net-retry self-test: OK"
