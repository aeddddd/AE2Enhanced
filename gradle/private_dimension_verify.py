#!/usr/bin/env python3
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "build" / "verification" / "private-dimension"
DONE = "PRIVATE_DIM_CLIENT_DONE"
FAILED = "PRIVATE_DIM_HARNESS_FAILED"


def prepare(clean):
    if clean and BASE.exists():
        shutil.rmtree(BASE)
    for role in ("server", "client-a", "client-b"):
        (BASE / role / "config").mkdir(parents=True, exist_ok=True)
        (BASE / role / "mods").mkdir(parents=True, exist_ok=True)
        if role != "server":
            (BASE / role / "options.txt").write_text("soundCategory_master:0.0\n", encoding="utf-8")
        for jar in ("FTBLib-5.4.7.2.jar", "FTBUtilities-5.4.1.131.jar"):
            shutil.copy2(ROOT / "libs-unused" / jar, BASE / role / "mods" / jar)
        ct_main = "CraftTweaker2-MC1120-Main-1.12-4.1.20.718" + ("-srg" if role == "server" else "") + ".jar"
        for jar in (ct_main, "CraftTweaker2-API-4.1.20.718.jar", "ZenScript-4.1.20.718.jar"):
            shutil.copy2(ROOT / "build" / "verification" / "mmce" / jar, BASE / role / "mods" / jar)
    (BASE / "server" / "scripts").mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / "src" / "verification" / "resources" / "ae2e_wp6.zs",
            BASE / "server" / "scripts" / "ae2e_wp6.zs")
    external = BASE / "server" / "config" / "ae2enhanced" / "guides" / "mymod"
    external.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / "src" / "verification" / "resources" / "ae2e_wp6_external.md",
            external / "external.md")
    (BASE / "server" / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (BASE / "server" / "server.properties").write_text(
        "spawn-protection=0\nmax-tick-time=60000\ngamemode=1\ndifficulty=0\n"
        "max-players=4\nserver-port=25566\nlevel-name=world\nlevel-type=DEFAULT\n"
        "view-distance=6\nonline-mode=false\nwhite-list=false\n"
        "enable-command-block=false\nmotd=AE2E private dimension isolated harness\n", encoding="utf-8")


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


def wait_for_resume(server, timeout=120):
    deadline = time.time() + timeout
    while time.time() < deadline:
        log = text(server["log"]) + text(Path(server["process_log"]))
        if "PRIVATE_DIM_RESUME_PASS" in log:
            return
        if server["process"].poll() is not None:
            raise RuntimeError("private-dimension resume server exited before PASS")
        time.sleep(2)
    raise TimeoutError("private-dimension persisted repaint did not resume")


def main():
    parser = argparse.ArgumentParser(description="Run isolated FTB/private-dimension acceptance with two clients")
    parser.add_argument("--clean", action="store_true")
    parser.add_argument("--timeout", type=int, default=600)
    parser.add_argument("--resume-only", action="store_true")
    args = parser.parse_args()
    prepare(args.clean)
    server = None
    clients = []
    try:
        if args.resume_only:
            server = launch("server", ["runServer", "-PserverRunDir=" + str(BASE / "server"),
                    "-PprivateDimensionHarness=true", "-PprivateRestartHarness=true", "--args=--nogui"])
            wait_for_resume(server)
            print("PRIVATE_DIMENSION_RESUME_PASS persisted repaint verified")
            return 0
        server = launch("server", ["runServer", "-PserverRunDir=" + str(BASE / "server"),
                "-PprivateDimensionHarness=true", "-PprivateRestartHarness=true", "--args=--nogui"])
        deadline = time.time() + args.timeout
        while time.time() < deadline:
            log = text(server["log"]) + text(Path(server["process_log"]))
            if "Done (" in log:
                break
            if server["process"].poll() is not None:
                raise RuntimeError("private-dimension server exited before clients started")
            time.sleep(2)
        else:
            raise TimeoutError("private-dimension server did not become ready")
        for role, username, peer in (("client-a", "DeveloperPDA", False),
                ("client-b", "DeveloperPDB", True)):
            run_dir = BASE / role
            arguments = ["runClient", "-PclientRunDir=" + str(run_dir),
                    "-PclientUsername=" + username, "-PprivateDimensionHarness=true",
                    "-PharnessServerAddress=localhost:25566"]
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
                states.append((DONE in log and "GUIDE_CLIENT_DONE" in log) or FAILED in log
                        or "GUIDE_HARNESS_FAILED" in log)
            if all(states):
                break
            for client in clients:
                if client["process"].poll() is not None:
                    log = text(client["log"]) + text(Path(client["process_log"]))
                    if DONE not in log:
                        raise RuntimeError("private-dimension client exited before PASS")
            time.sleep(2)
        else:
            raise TimeoutError("private-dimension clients did not finish")
        failures = []
        for client in clients:
            log = text(client["log"]) + text(Path(client["process_log"]))
            if DONE not in log or FAILED in log or "PRIVATE_DIM_CLIENT_PASS" not in log:
                failures.append(str(client["log"]) + ": missing floor/role PASS")
            if "GUIDE_CLIENT_PASS" not in log or "GUIDE_HARNESS_FAILED" in log:
                failures.append(str(client["log"]) + ": missing guide sync PASS")
        peer_log = text(clients[1]["log"]) + text(Path(clients[1]["process_log"]))
        if "PRIVATE_DIM_LEFT_CLIENT_PASS" not in peer_log:
            failures.append("client-b: missing safe team-left PASS")
        server_log = text(server["log"]) + text(Path(server["process_log"]))
        if "PRIVATE_DIM_SERVER_PASS" not in server_log or FAILED in server_log:
            failures.append("server: missing repaint/team PASS")
        if "PRIVATE_DIM_RESTART_ARMED" not in server_log:
            failures.append("server: missing explicit restart arming")
        if failures:
            raise RuntimeError("\n".join(failures))
        try:
            server["process"].stdin.write(b"stop\n")
            server["process"].stdin.flush()
            server["process"].wait(timeout=60)
        except Exception:
            pass
        server = launch("server", ["runServer", "-PserverRunDir=" + str(BASE / "server"),
                "-PprivateDimensionHarness=true", "-PprivateRestartHarness=true", "--args=--nogui"])
        wait_for_resume(server)
        print("PRIVATE_DIMENSION_PASS two clients; OWNER/MEMBER, FTB enter, repaint, restart and team-left verified")
        return 0
    except Exception as error:
        print("PRIVATE_DIMENSION_FAILED " + str(error), file=sys.stderr)
        return 1
    finally:
        for client in clients:
            stop_process(client)
        if server is not None:
            stop_process(server)


if __name__ == "__main__":
    sys.exit(main())
