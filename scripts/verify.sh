#!/usr/bin/env bash
# Builds the mod, runs its unit tests, then the two production client runs on a private Xvfb display:
# verifyRecording (Flashback + this mod: record, check the records, play back) and verifyStockPlayback
# (Flashback alone plays the same replay). Results: build/verify/{record-summary.txt,playback-*.txt,sample.zip}.
#
# Usage: scripts/verify.sh [display number, default 127]
# The whole run is bounded (VERIFY_TIMEOUT, default 40m); on exit any client it started and the display are stopped.
# Run memory-heavy work through the host's job runner, with display settings inside the job, for example:
#   heavy --label fbs -- env -u WAYLAND_DISPLAY XDG_SESSION_TYPE=x11 XDG_RUNTIME_DIR=/tmp/fbs-xdg scripts/verify.sh
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
display=":${1:-127}"
if [[ -e "/tmp/.X${display#:}-lock" || -e "/tmp/.X11-unix/X${display#:}" ]]; then
  echo "display $display is in use" >&2
  exit 2
fi
if [[ -n "${XDG_RUNTIME_DIR:-}" ]]; then
  mkdir -p -m 700 "$XDG_RUNTIME_DIR"
fi
mkdir -p build
Xvfb "$display" -screen 0 1280x720x24 -nolisten tcp > build/xvfb.log 2>&1 &
xvfb=$!
# the clients carry -Dflashbackserverbound.verify=...: stop any still running, then the display (each step may find
# nothing to stop, which must not end the trap under set -e)
trap 'pkill -f -- "-Dflashbackserverbound.verify=" 2>/dev/null || true; kill "$xvfb" 2>/dev/null || true' EXIT
for _ in $(seq 50); do
  [[ -e "/tmp/.X11-unix/X${display#:}" ]] && break
  sleep 0.2
done
export DISPLAY="$display" ALSOFT_DRIVERS=null
timeout --kill-after=60 "${VERIFY_TIMEOUT:-40m}" \
  ./gradlew --no-daemon --console=plain --continue build verifyRecording verifyStockPlayback
