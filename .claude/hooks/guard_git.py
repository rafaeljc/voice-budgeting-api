#!/usr/bin/env python3
"""PreToolUse (Bash): blocks git/gh commands forbidden to the agent and requires approval to create or edit PRs.

Exit 2 blocks the command and returns the stderr message to the agent. The "ask" decision makes Claude Code
prompt the user even if the agent skips the approval step of the /pr skill.
"""
import json
import re
import sys

# `git` followed by optional global options (e.g. `git -C dir push`)
GIT = r"\bgit(?:\s+-\S+(?:\s+[^\s-]\S*)?)*\s+"

BLOCKED = [
    (re.compile(GIT + r"push\b"), "git push is user-only."),
    (re.compile(GIT + r"merge\b"), "git merge is user-only."),
    (re.compile(r"\bgh\s+pr\s+merge\b"), "merging a PR is user-only."),
    (re.compile(r"merge\.py\b.*--execute"), "merging a PR is user-only."),
    (re.compile(r"\bgit\b.*--no-verify"), "--no-verify skips commit validation. Fix the message instead."),
]

NEEDS_APPROVAL = [
    (re.compile(r"\bgh\s+pr\s+(?:create|edit)\b"), "creating or editing a PR requires user approval."),
]


def main() -> int:
    command = json.load(sys.stdin).get("tool_input", {}).get("command", "")
    for pattern, reason in BLOCKED:
        if pattern.search(command):
            print(f"Blocked: {reason}", file=sys.stderr)
            return 2
    for pattern, reason in NEEDS_APPROVAL:
        if pattern.search(command):
            print(json.dumps({"hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "ask",
                "permissionDecisionReason": reason,
            }}))
            return 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
