#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-}" = true
test "${RUNNER_OS:-}" = Linux

diagnostics="androidApp/build/smoke"
mkdir -p "$diagnostics"
sdk_tools="$ANDROID_HOME/cmdline-tools/latest/bin"
image="system-images;android-37;google_apis_ps16k;x86_64"
serial=emulator-5554
package=de.westnordost.streetcomplete.expert.debug
avd=scee-ci-fixme
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
if adb -s "$serial" get-state >/dev/null 2>&1; then
    echo "Refusing to use an existing emulator" >&2
    exit 1
fi
sudo chmod a+rw /dev/kvm
set +o pipefail
yes | "$sdk_tools/sdkmanager" --licenses >/dev/null
license_status=${PIPESTATUS[1]}
set -o pipefail
test "$license_status" = 0
"$sdk_tools/sdkmanager" platform-tools emulator "$image"
printf 'no\n' | "$sdk_tools/avdmanager" create avd --force --name "$avd" --package "$image"
emulator -avd "$avd" -port 5554 -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader -memory 2048 -cores 2 > "$diagnostics/emulator.log" 2>&1 &
emulator_pid=$!

capture_diagnostics() {
    local status=$?
    if [[ "$(adb -s "$serial" shell getprop ro.boot.qemu.avd_name 2>/dev/null | tr -d '\r')" = "$avd" ]]; then
        adb -s "$serial" logcat -d > "$diagnostics/logcat.txt" 2>&1 || true
        adb -s "$serial" exec-out screencap -p > "$diagnostics/final.png" 2>/dev/null || true
        adb -s "$serial" emu kill || true
    else
        kill "$emulator_pid" 2>/dev/null || true
    fi
    wait "$emulator_pid" || true
    return "$status"
}
trap capture_diagnostics EXIT
for attempt in $(seq 1 120); do
    if [[ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]]; then
        break
    fi
    kill -0 "$emulator_pid"
    sleep 2
done
test "$(adb -s "$serial" shell getprop ro.boot.qemu.avd_name | tr -d '\r')" = "$avd"
test "$(adb -s "$serial" shell getprop sys.boot_completed | tr -d '\r')" = 1
test "$(adb -s "$serial" shell getconf PAGESIZE | tr -d '\r')" = 16384
adb -s "$serial" install androidApp/build/ui-apk/androidApp-debug.apk
adb -s "$serial" shell pm grant "$package" android.permission.ACCESS_COARSE_LOCATION
adb -s "$serial" shell pm grant "$package" android.permission.ACCESS_FINE_LOCATION
adb -s "$serial" shell pm grant "$package" android.permission.POST_NOTIFICATIONS
adb -s "$serial" emu geo fix 13.405 52.52025
adb -s "$serial" shell am start -W -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER -p "$package"
for attempt in $(seq 1 30); do
    if adb -s "$serial" shell run-as "$package" ls databases/streetcomplete_v2.db >/dev/null 2>&1; then
        break
    fi
    sleep 1
done
adb -s "$serial" shell am force-stop "$package"
adb -s "$serial" exec-out run-as "$package" tar -cf - databases shared_prefs > "$diagnostics/baseline.tar"
python3 .github/scripts/seed-fixme-runtime.py "$diagnostics/baseline.tar" "$diagnostics/fixme-fixture"
adb -s "$serial" shell run-as "$package" rm -f databases/streetcomplete_v2.db-wal databases/streetcomplete_v2.db-shm
adb -s "$serial" exec-in run-as "$package" tar -xf - < "$diagnostics/fixme-fixture/fixture.tar"
adb -s "$serial" shell svc wifi disable
adb -s "$serial" shell svc data disable

maestro_dir="$RUNNER_TEMP/scee-maestro"
mkdir -p "$maestro_dir"
curl --fail --location --retry 3 --connect-timeout 20 --max-time 300 \
    https://github.com/mobile-dev-inc/Maestro/releases/download/cli-2.12.0/maestro.zip \
    --output "$maestro_dir/maestro.zip"
printf '%s  %s\n' a6044a84542f522f434c5dc3d609e60c7446d423f3ed1b2ae513f5c48663ec5e \
    "$maestro_dir/maestro.zip" | sha256sum --check
unzip -q "$maestro_dir/maestro.zip" -d "$maestro_dir/extracted"
maestro_bin=$(find "$maestro_dir/extracted" -type f -path '*/bin/maestro' -print -quit)
test -n "$maestro_bin"
chmod +x "$maestro_bin"
"$maestro_bin" --device "$serial" test --format junit --output "$diagnostics/junit.xml" \
    --test-output-dir "$diagnostics/maestro" .github/maestro/android-fixme.yaml

adb -s "$serial" shell am force-stop "$package"
adb -s "$serial" exec-out run-as "$package" tar -cf - databases > "$diagnostics/after.tar"
python3 - "$diagnostics" <<'PY'
import json
from pathlib import Path
import sqlite3
import sys
import tarfile

root = Path(sys.argv[1])
with tarfile.open(root / "after.tar") as archive:
    for member in archive.getmembers():
        if member.isfile() and member.name.startswith("databases/"):
            target = (root / "after" / member.name).resolve()
            assert target.is_relative_to((root / "after").resolve())
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.extractfile(member).read())
connection = sqlite3.connect(root / "after/databases/streetcomplete_v2.db")
queues = {table: connection.execute("select count(*) from " + table).fetchone()[0]
          for table in ("osm_element_edits", "osm_note_edits")}
assert not any(queues.values()), queues
result = {"sdk": 37, "page_size": 16384, "quest": "ShowFixme", "form_visible": True,
          "queued_osm_writes": queues, "fixture": "Synthetic, disposable CI application data"}
(root / "runtime-result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
print(json.dumps(result))
PY
