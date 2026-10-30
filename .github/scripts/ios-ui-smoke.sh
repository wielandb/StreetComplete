#!/usr/bin/env bash
set -euo pipefail

diagnostics="iosApp/build/smoke/ui"
mkdir -p "$diagnostics"
device_id=$(python3 - iosApp/build/smoke/devices.json <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    runtimes = json.load(stream)["devices"]
devices = [device for runtime, entries in runtimes.items() if ".iOS-" in runtime
           for device in entries if device.get("isAvailable") and device["name"].startswith("iPhone")]
print(next((device for device in devices if device["state"] == "Booted"), devices[0])["udid"])
PY
)
[[ "$device_id" =~ ^[0-9A-Fa-f-]{36}$ ]]
xcrun simctl bootstatus "$device_id" -b
xcrun simctl location "$device_id" set 52.52,13.405

# Run the local CLI against this disposable simulator; no cloud account is used.
maestro_dir="$RUNNER_TEMP/scee-maestro"
mkdir -p "$maestro_dir"
curl --fail --location --retry 3 --connect-timeout 20 --max-time 300 \
    https://github.com/mobile-dev-inc/Maestro/releases/download/cli-2.12.0/maestro.zip \
    --output "$maestro_dir/maestro.zip"
printf '%s  %s\n' \
    a6044a84542f522f434c5dc3d609e60c7446d423f3ed1b2ae513f5c48663ec5e \
    "$maestro_dir/maestro.zip" | shasum -a 256 --check
unzip -q "$maestro_dir/maestro.zip" -d "$maestro_dir/extracted"
maestro_bin=$(find "$maestro_dir/extracted" -type f -path '*/bin/maestro' -print -quit)
test -n "$maestro_bin"
chmod +x "$maestro_bin"
"$maestro_bin" --version | tee "$diagnostics/maestro-version.txt"

capture_diagnostics() {
    local status=$?
    "$maestro_bin" --device "$device_id" hierarchy > "$diagnostics/hierarchy.txt" 2>&1 || true
    xcrun simctl io "$device_id" screenshot "$diagnostics/final.png" || true
    xcrun simctl spawn "$device_id" log show --last 5m --style compact \
        --predicate 'process == "SCEE"' > "$diagnostics/app.log" 2>&1 || true
    return "$status"
}
trap capture_diagnostics EXIT

"$maestro_bin" --device "$device_id" test \
    --format junit --output "$diagnostics/junit.xml" \
    --test-output-dir "$diagnostics/maestro" \
    .github/maestro/ios.yaml

app_data=$(xcrun simctl get_app_container "$device_id" de.westnordost.streetcomplete.expert data)
python3 - "$app_data/Library/Preferences/de.westnordost.streetcomplete.expert.plist" \
    "$diagnostics/preferences.json" <<'PY'
import json
import plistlib
import sys

with open(sys.argv[1], "rb") as stream:
    preferences = plistlib.load(stream)
actual = {key: preferences.get(key) for key in ("expert_mode", "display.zoomButtons")}
with open(sys.argv[2], "w", encoding="utf-8") as stream:
    json.dump(actual, stream, indent=2)
assert all(value is True for value in actual.values()), actual
print("Expert mode and screen zoom controls remain enabled after application restart.")
PY
