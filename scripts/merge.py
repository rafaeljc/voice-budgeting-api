#!/usr/bin/env python3
"""Squash merge following the project convention: PR title + (#N); body with only the Mudanças bullets.

Usage: scripts/merge.py <PR number> [--execute]   (without --execute, only prints the result)
User-only tool: .claude/hooks/guard_git.py blocks the agent from running --execute.
"""
import argparse
import json
import re
import subprocess
import sys

TITLE = re.compile(r"^(feat|fix|refactor|perf|test|docs|build|ci|chore): [^A-Z\s].*[^.]$")
CHANGES_HEADING = "## Mudanças"


def pull_request(number: int) -> dict:
    output = subprocess.run(
        ["gh", "pr", "view", str(number), "--json", "title,body"],
        check=True, capture_output=True, text=True,
    ).stdout
    return json.loads(output)


def changes(body: str) -> list[str]:
    bullets, inside = [], False
    for line in body.replace("\r", "").splitlines():
        if line.startswith("## "):
            inside = line.strip() == CHANGES_HEADING
        elif inside and re.match(r"^- \S", line):
            bullets.append(line)
    return bullets


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("number", type=int)
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args()

    pr = pull_request(args.number)
    if not TITLE.match(pr["title"]):
        print(f"PR title does not follow the convention: {pr['title']}", file=sys.stderr)
        return 1

    bullets = changes(pr["body"])
    if not bullets:
        print(f"PR #{args.number} has no bullets in the Mudanças section.", file=sys.stderr)
        return 1

    subject = f"{pr['title']} (#{args.number})"
    body = "\n".join(bullets)
    print(f"{subject}\n\n{body}")

    if args.execute:
        subprocess.run(
            ["gh", "pr", "merge", str(args.number), "--squash",
             "--subject", subject, "--body", body, "--delete-branch"],
            check=True,
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
