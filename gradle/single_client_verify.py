#!/usr/bin/env python3
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "build" / "verification" / "single-client"
DONE = "SPLICED_TERM_HARNESS_DONE"
FAILED = "SPLICED_TERM_HARNESS_FAILED"
HEI = "HadEnoughItems_1.12.2-4.29.15.jar"


def prepare(clean, gui_scale):
    if clean and BASE.exists():
        shutil.rmtree(BASE)
    (BASE / "config").mkdir(parents=True, exist_ok=True)
    (BASE / "mods").mkdir(parents=True, exist_ok=True)
    (BASE / "options.txt").write_text(
            f"guiScale:{gui_scale}\noverrideWidth:854\noverrideHeight:480\npauseOnLostFocus:false\nsoundCategory_master:0.0\n",
            encoding="utf-8")
    (BASE / "config" / "ae2enhanced.cfg").write_text(
        "# Configuration file\n\ngeneral {\n    terminal {\n"
        "        B:splicedTerminalHarnessAutoWorld=true\n"
        "        B:splicedTerminalTestHarness=true\n    }\n}\n", encoding="utf-8")
    shutil.copy2(ROOT / "run" / "mods" / HEI, BASE / "mods" / HEI)


def command():
    if os.name == "nt":
        return [os.environ.get("COMSPEC", "cmd.exe"), "/c", "gradlew.bat", "runClient",
                "-PclientRunDir=" + str(BASE), "-PclientUsername=DeveloperWP8",
                "-PclientUuid=00000000-0000-0000-0000-00000000a350"]
    return ["bash", "gradlew", "runClient", "-PclientRunDir=" + str(BASE),
            "-PclientUsername=DeveloperWP8", "-PclientUuid=00000000-0000-0000-0000-00000000a350"]


def main():
    global BASE
    parser = argparse.ArgumentParser(description="Run isolated HEI single-client and omni-tool acceptance")
    parser.add_argument("--clean", action="store_true")
    parser.add_argument("--timeout", type=int, default=600)
    parser.add_argument("--gui-scale", type=int, choices=(1, 2, 3, 4), default=1)
    parser.add_argument("--run-dir", type=Path)
    parser.add_argument("--visual-only", action="store_true")
    args = parser.parse_args()
    if args.run_dir is not None:
        BASE = args.run_dir.resolve()
    elif args.visual_only:
        BASE = ROOT / "build" / "verification" / f"single-client-visual-scale-{args.gui_scale}"
    elif args.gui_scale != 1:
        BASE = ROOT / "build" / "verification" / f"single-client-scale-{args.gui_scale}"
    prepare(args.clean, args.gui_scale)
    environment = os.environ.copy()
    if args.visual_only:
        environment["JAVA_TOOL_OPTIONS"] = environment.get("JAVA_TOOL_OPTIONS", "") + " -Dae2e.spliced.visualOnly=true"
    stream = (BASE / "client.process.log").open("w", encoding="utf-8")
    process = subprocess.Popen(command(), cwd=str(ROOT), stdin=subprocess.PIPE,
            stdout=stream, stderr=subprocess.STDOUT, env=environment)
    try:
        deadline = time.time() + args.timeout
        while time.time() < deadline:
            log = read(BASE / "logs" / "latest.log") + read(BASE / "client.process.log")
            if DONE in log or FAILED in log:
                break
            if process.poll() is not None:
                raise RuntimeError("single client exited before acceptance completed")
            time.sleep(2)
        else:
            raise TimeoutError("single-client acceptance did not finish")
        failures = []
        if DONE not in log or FAILED in log:
            failures.append("missing clean harness completion")
        if "ACCEPT_ALL_DONE" not in log:
            failures.append("missing acceptance summary")
        failed_checks = re.findall(r"ACCEPT [^\r\n]+: FAIL[^\r\n]*", log)
        if failed_checks:
            failures.extend(failed_checks)
        checks = ("A25-screenshot", "A26-screenshot", "A27-screenshot") if args.visual_only else (
                "A2", "A23", "A24", "A25-crafting-clear", "A25-pattern-geometry", "A25-interface",
                "A25-interface-take", "A25-interface-put", "A25-interface-hidden", "A28",
                "A29-transfer", "A30-D4", "A32", "A33", "A34", "A35")
        for check in checks:
            if not re.search(r"ACCEPT " + re.escape(check) + r": PASS", log):
                failures.append("missing PASS for " + check)
        if args.visual_only:
            for image in ("spliced-default.png", "spliced-amount.png", "spliced-editor.png"):
                if not (BASE / image).is_file():
                    failures.append("missing visual evidence " + image)
        if failures:
            raise RuntimeError("\n".join(failures))
        print("SPLICED_VISUAL_PASS three framebuffer screenshots" if args.visual_only else
                "SINGLE_CLIENT_PASS HEI terminal matrix and A32-A35 real ring conversion/config GUI verified")
        return 0
    except Exception as error:
        print("SINGLE_CLIENT_FAILED " + str(error), file=sys.stderr)
        return 1
    finally:
        if process.poll() is None:
            if os.name == "nt":
                subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            else:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
        stream.close()


def read(path):
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except FileNotFoundError:
        return ""


if __name__ == "__main__":
    sys.exit(main())
