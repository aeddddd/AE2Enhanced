#!/usr/bin/env python3
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "build" / "verification" / "e5"
DONE = "SPLICED_TERM_HARNESS_DONE"
FAILED = "SPLICED_TERM_HARNESS_FAILED"


def prepare(clean, port):
    if clean and BASE.exists():
        shutil.rmtree(BASE)
    config = "# Configuration file\n\ngeneral {\n    terminal {\n        B:splicedTerminalHarnessAutoWorld=false\n        B:splicedTerminalTestHarness=true\n    }\n}\n"
    for role in ("server", "client-a", "client-b"):
        directory = BASE / role / "config"
        directory.mkdir(parents=True, exist_ok=True)
        (directory / "ae2enhanced.cfg").write_text(config, encoding="utf-8")
        (BASE / role / "mods").mkdir(parents=True, exist_ok=True)
        if role != "server":
            (BASE / role / "options.txt").write_text("soundCategory_master:0.0\n", encoding="utf-8")
    (BASE / "server" / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (BASE / "server" / "server.properties").write_text(
        "spawn-protection=0\nmax-tick-time=60000\ngamemode=1\ndifficulty=0\n"
        f"max-players=4\nserver-port={port}\nlevel-name=world\nlevel-type=DEFAULT\n"
        "view-distance=6\nonline-mode=false\nwhite-list=false\n"
        "enable-command-block=false\nmotd=AE2E E5 isolated harness\n", encoding="utf-8")


def command(arguments):
    if os.name == "nt":
        return [os.environ.get("COMSPEC", "cmd.exe"), "/c", "gradlew.bat"] + arguments
    return ["bash", "gradlew"] + arguments


def launch(role, arguments):
    log_path = BASE / role / "logs" / "latest.log"
    stream = (BASE / (role + ".process.log")).open("w", encoding="utf-8")
    process = subprocess.Popen(command(arguments), cwd=str(ROOT), stdin=subprocess.PIPE,
            stdout=stream, stderr=subprocess.STDOUT)
    return {"process": process, "stream": stream, "log": log_path, "process_log": stream.name}


def text(path):
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except FileNotFoundError:
        return ""


def stop_process(role):
    process = role["process"]
    if process.poll() is not None:
        role["stream"].close()
        return
    if os.name == "nt":
        subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    else:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
    role["stream"].close()


def main():
    parser = argparse.ArgumentParser(description="Run isolated two-client E5 acceptance without desktop input")
    parser.add_argument("--clean", action="store_true")
    parser.add_argument("--timeout", type=int, default=600)
    parser.add_argument("--tii", action="store_true", help="also verify real TII resource monitor initialization")
    parser.add_argument("--port", type=int, default=25567)
    args = parser.parse_args()
    prepare(args.clean, args.port)
    if args.tii:
        for role in ("server", "client-a", "client-b"):
            shutil.copy2(ROOT / "libs" / "terminal_interaction_integration-1.0.jar",
                    BASE / role / "mods" / "terminal_interaction_integration-1.0.jar")
    server = None
    clients = []
    try:
        server = launch("server", ["runServer", "-PserverRunDir=" + str(BASE / "server"),
                "-PharnessE5=true", "--args=--nogui"])
        deadline = time.time() + args.timeout
        while time.time() < deadline:
            log = text(server["log"]) + text(Path(server["process_log"]))
            if "Done (" in log or "E 系" in log and "自检" in log:
                break
            if server["process"].poll() is not None:
                raise RuntimeError("E5 server exited before clients started")
            time.sleep(2)
        else:
            raise TimeoutError("E5 server did not become ready")
        for role, username, peer in (("client-a", "DeveloperE5A", False),
                ("client-b", "DeveloperE5B", True)):
            run_dir = BASE / role
            arguments = ["runClient", "-PclientRunDir=" + str(run_dir),
                    "-PclientUsername=" + username, "-PharnessE5=true",
                    "-PharnessServerAddress=localhost:" + str(args.port)]
            if peer:
                arguments.append("-PharnessE5Peer=true")
                arguments.append("-PclientUuid=00000000-0000-0000-0000-00000000e5b2")
            else:
                arguments.append("-PclientUuid=00000000-0000-0000-0000-00000000e5a1")
            clients.append(launch(role, arguments))
            time.sleep(3)
        while time.time() < deadline:
            states = []
            for client in clients:
                log = text(client["log"]) + text(Path(client["process_log"]))
                states.append(DONE in log or FAILED in log)
            if all(states):
                break
            for client in clients:
                if client["process"].poll() is not None:
                    log = text(client["log"]) + text(Path(client["process_log"]))
                    if DONE not in log:
                        raise RuntimeError("E5 client exited before PASS")
            time.sleep(2)
        else:
            raise TimeoutError("E5 clients did not finish")
        server_log = text(server["log"]) + text(Path(server["process_log"]))
        failures = []
        for client in clients:
            log = text(client["log"]) + text(Path(client["process_log"]))
            if DONE not in log or FAILED in log or "ACCEPT E5-" not in log:
                failures.append(str(client["log"]) + ": missing clean E5 PASS")
            if "ACCEPT E5-query: PASS" not in log or "ACCEPT E5-switch: PASS" not in log \
                    or "ACCEPT E5-layout: PASS" not in log:
                failures.append(str(client["log"]) + ": incomplete E5 query/layout matrix")
        if "ACCEPT E5-server: PASS" not in server_log:
            failures.append("server: missing E5-server session isolation PASS")
        if args.tii:
            all_logs = server_log + "".join(text(client["log"]) + text(Path(client["process_log"]))
                    for client in clients)
            if "ResourceFakeMonitor initialized in NetworkInventoryHandler for: ae2enhanced:energy" not in all_logs:
                failures.append("TII: energy ResourceFakeMonitor did not initialize")
            if "Failed to initialize resource monitors" in all_logs:
                failures.append("TII: resource monitor initialization failure remains")
        if failures:
            raise RuntimeError("\n".join(str(failure) for failure in failures))
        try:
            server["process"].stdin.write(b"stop\n")
            server["process"].stdin.flush()
            server["process"].wait(timeout=60)
        except Exception:
            pass
        print("E5_PASS two independent clients; query/generation/session/layout isolation verified")
        return 0
    except Exception as error:
        print("E5_FAILED " + str(error), file=sys.stderr)
        return 1
    finally:
        for client in clients:
            stop_process(client)
        if server is not None:
            stop_process(server)


if __name__ == "__main__":
    sys.exit(main())
