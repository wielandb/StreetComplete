#!/usr/bin/env bash
set -euo pipefail

app="iosApp/build/derived-data/Build/Products/Debug-iphonesimulator/SCEE.app"
diagnostics="iosApp/build/smoke"
mkdir -p "$diagnostics"
test -d "$app"
bundle_id=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$app/Info.plist")
test "$bundle_id" = "de.westnordost.streetcomplete.expert"

xcrun simctl list devices available --json > "$diagnostics/devices.json"
device_id=$(python3 - "$diagnostics/devices.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    runtimes = json.load(stream)["devices"]
devices = [device for runtime, entries in runtimes.items() if ".iOS-" in runtime
           for device in entries if device.get("isAvailable") and device["name"].startswith("iPhone")]
if not devices:
    raise SystemExit("No available iPhone simulator")
device = next((device for device in devices if device["state"] == "Booted"), devices[0])
print(device["udid"])
PY
)
[[ "$device_id" =~ ^[0-9A-Fa-f-]{36}$ ]]
xcrun simctl bootstatus "$device_id" -b

capture_diagnostics() {
    local status=$?
    xcrun simctl io "$device_id" screenshot "$diagnostics/launch.png" || true
    xcrun simctl spawn "$device_id" log show --last 2m --style compact \
        --predicate 'process == "SCEE"' > "$diagnostics/app.log" 2>&1 || true
    return "$status"
}
trap capture_diagnostics EXIT

xcrun simctl install "$device_id" "$app"
launch_output=$(xcrun simctl launch --terminate-running-process "$device_id" "$bundle_id")
printf '%s\n' "$launch_output" | tee "$diagnostics/launch.txt"
app_pid=${launch_output##*: }
[[ "$app_pid" =~ ^[0-9]+$ ]]
sleep 20
kill -0 "$app_pid"
ps -p "$app_pid" -o pid=,comm= | tee "$diagnostics/process.txt"
echo "SCEE remains running 20 seconds after simulator launch."
