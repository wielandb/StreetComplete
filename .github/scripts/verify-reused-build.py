import json
import os
from pathlib import Path
import re
import subprocess
from urllib.request import Request, urlopen

run_id = os.environ["BUILD_RUN_ID"]
repository = os.environ["GITHUB_REPOSITORY"]
if not run_id.isdecimal():
    raise SystemExit("Expected a numeric application build run ID")
request = Request(
    f"https://api.github.com/repos/{repository}/actions/runs/{run_id}",
    headers={"Authorization": "Bearer " + os.environ["GH_TOKEN"],
             "Accept": "application/vnd.github+json", "User-Agent": "SCEE UI verification"},
)
with urlopen(request, timeout=30) as response:
    run = json.load(response)
if run["repository"]["full_name"] != repository or run["head_repository"]["full_name"] != repository:
    raise SystemExit("Application artifact belongs to a different repository")
source = run["head_sha"]
if not re.fullmatch(r"[0-9a-f]{40}", source):
    raise SystemExit("Invalid application source revision")
changed = subprocess.check_output(["git", "diff", "--name-only", source, "HEAD"], text=True).splitlines()
unexpected = [path for path in changed if not path.startswith(".github/")
              and path != "res/documentation/upstream-64-integration.md"]
if unexpected:
    raise SystemExit("Application code differs from the reused build: " + ", ".join(unexpected))
diagnostics = Path("androidApp/build/smoke")
diagnostics.mkdir(parents=True, exist_ok=True)
(diagnostics / "application-source.json").write_text(
    json.dumps({"run_id": run_id, "source_sha": source, "changed_ci_or_documentation": changed}, indent=2),
    encoding="utf-8",
)
print("Reused application matches current app source:", source)
