#!/usr/bin/env python3
"""PostToolUse (Edit|Write): formats the edited .java file with Spotless.

Exit 2 returns the formatter error (stderr) to the agent; any other case exits 0 and does nothing.
"""
import json
import os
import subprocess
import sys
from pathlib import Path


def main() -> int:
    payload = json.load(sys.stdin)
    file_path = payload.get("tool_input", {}).get("file_path", "")
    project_dir = Path(os.environ.get("CLAUDE_PROJECT_DIR") or payload.get("cwd") or Path.cwd()).resolve()
    path = Path(file_path).resolve() if file_path else None

    if path is None or path.suffix != ".java" or not path.is_file() or not path.is_relative_to(project_dir):
        return 0

    result = subprocess.run(
        ["./gradlew", "spotlessApply", "-q", f"-PspotlessIdeHook={path}"],
        cwd=project_dir,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        print(f"spotlessApply failed for {path}:\n{result.stdout}{result.stderr}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
