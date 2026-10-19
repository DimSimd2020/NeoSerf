#!/usr/bin/env python3
"""Copy named XCTest screenshot attachments out of an .xcresult bundle."""
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path


def xc(args):
    return subprocess.check_output(["xcrun", "xcresulttool", *args])


def get_json(xcresult, ident=None):
    args = ["get", "--legacy", "--format", "json", "--path", xcresult]
    if ident:
        args += ["--id", ident]
    return json.loads(xc(args))


def export_file(xcresult, ident, dest):
    subprocess.check_call(
        [
            "xcrun",
            "xcresulttool",
            "export",
            "--legacy",
            "--type",
            "file",
            "--path",
            xcresult,
            "--id",
            ident,
            "--output-path",
            str(dest),
        ]
    )


def walk_attachments(node, found):
    if not isinstance(node, dict):
        return
    name = None
    payload = None
    name_node = node.get("name")
    if isinstance(name_node, dict):
        name = name_node.get("_value")
    payload_node = node.get("payloadRef") or node.get("exportablePayloadRef")
    if isinstance(payload_node, dict):
        payload = payload_node.get("id", {}).get("_value") if isinstance(payload_node.get("id"), dict) else payload_node.get("_value")
        if payload is None and "id" in payload_node and isinstance(payload_node["id"], str):
            payload = payload_node["id"]
    if name and payload:
        found.append((name, payload))
    for value in node.values():
        if isinstance(value, dict):
            walk_attachments(value, found)
        elif isinstance(value, list):
            for item in value:
                if isinstance(item, dict):
                    walk_attachments(item, found)


def copy_loose_files(xcresult, outdir):
    copied = 0
    for path in Path(xcresult).rglob("*"):
        if path.suffix.lower() in {".png", ".jpg", ".jpeg"}:
            dest = outdir / path.name
            shutil.copy2(path, dest)
            copied += 1
    return copied


def main():
    if len(sys.argv) != 3:
        print("usage: export_xcresult_screenshots.py <xcresult> <outdir>", file=sys.stderr)
        return 2
    xcresult = sys.argv[1]
    outdir = Path(sys.argv[2])
    outdir.mkdir(parents=True, exist_ok=True)

    copied = 0
    try:
        subprocess.check_call(
            [
                "xcrun",
                "xcresulttool",
                "export",
                "attachments",
                "--path",
                xcresult,
                "--output-path",
                str(outdir),
            ]
        )
        copied = len(list(outdir.glob("*.png"))) + len(list(outdir.glob("*.jpeg")))
        if copied:
            print(f"xcresulttool export attachments wrote {copied} files")
            return 0
    except Exception as exc:
        print(f"xcresulttool export attachments unavailable: {exc}", file=sys.stderr)

    try:
        root = get_json(xcresult)
        found = []
        walk_attachments(root, found)
        # Also follow testsRef / attachments from action results.
        for action in root.get("actions", {}).get("_values", []) or root.get("actions", {}).get("values", []) or []:
            result = action.get("actionResult", {})
            tests_ref = result.get("testsRef", {}).get("id", {}).get("_value")
            if tests_ref:
                walk_attachments(get_json(xcresult, tests_ref), found)
        seen = set()
        for name, payload in found:
            stem = Path(name).stem.replace(" ", "-")
            if not stem or stem in seen:
                continue
            dest = outdir / f"{stem}.png"
            try:
                export_file(xcresult, payload, dest)
                seen.add(stem)
                copied += 1
                print(f"exported {dest.name}")
            except subprocess.CalledProcessError as exc:
                print(f"skip {stem}: {exc}", file=sys.stderr)
    except Exception as exc:
        print(f"xcresulttool parse failed: {exc}", file=sys.stderr)

    if copied == 0:
        copied = copy_loose_files(xcresult, outdir)
        print(f"copied {copied} loose image files")
    print(f"exported {copied} attachments to {outdir}")
    return 0 if copied else 1


if __name__ == "__main__":
    sys.exit(main())
