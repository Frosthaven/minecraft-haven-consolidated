"""Runs the built mod against the Haven Snapshot world, headless, and reports what the audit found.

1. Prism instance "Haven Consolidated Test" = a copy of "Haven Snapshot" (the pack's client with the downloaded
   world) with the jar from build/libs in mods/ and the JVM flags that make the mod audit itself and quit.
   `--instance <name>` copies another Prism instance instead (a newer pack's client, e.g. "Haven Dev": its mods,
   configs and NeoForge) and brings the snapshot world over from "Haven Snapshot"; `--skip <path>` (repeatable)
   deletes more of the copy, e.g. a dev-only jar: `--skip mods/zz-haven-harness.jar`.
2. Launches it on the snapshot world under Xvfb (offline player "Tester"), waits for haven-consolidated-audit.json,
   then for the game to exit.
3. Prints the audit summary, the mod's log lines and any mixin or crash lines, and runs tools/world_scan.py on the
   saved world: the chunks the session loaded hold none of the old ids any more.

Run: ./gradlew build, then  uv run --with nbtlib --with numpy python -I tools/snapshot_test.py [--keep]
     [--instance <Prism instance>] [--skip <relative path>]...
Needs: prismlauncher, xvfb-run. --keep leaves the test instance for a look in Prism (else it is deleted).
"""
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
PRISM = Path.home() / ".local/share/PrismLauncher"
SNAPSHOT = PRISM / "instances/Haven Snapshot"   # the pack client with the owner's downloaded world
NAME = "Haven Consolidated Test"
DST = PRISM / "instances" / NAME
GAME = DST / "minecraft"
WORLD = "Haven Snapshot 2026-10-12"
SKIP = ["logs", "screenshots", "crash-reports", "journeymap/data"]
JVM_ARGS = "-Dhaven_consolidated.audit=true -Dhaven_consolidated.audit.quit=true -Dhaven_consolidated.audit.ticks=1200"
TIMEOUT = 30 * 60


def jar() -> Path:
    version = re.search(r"^mod_version=(.*)$", (REPO / "gradle.properties").read_text(), re.M).group(1).strip()
    path = REPO / "build/libs" / f"haven-consolidated-{version}.jar"
    if not path.exists():
        sys.exit(f"{path} missing: run ./gradlew build")
    return path


def arg(flag, default):
    return sys.argv[sys.argv.index(flag) + 1] if flag in sys.argv else default


def copy_instance(src: Path, skip: list):
    if DST.exists():
        shutil.rmtree(DST)
    subprocess.run(["cp", "-a", "--reflink=auto", str(src), str(DST)], check=True)
    for rel in SKIP + skip:
        path = GAME / rel
        if path.is_file():
            path.unlink()
        else:
            shutil.rmtree(path, ignore_errors=True)
    world = GAME / "saves" / WORLD
    if not world.exists():   # another pack client: it runs the snapshot world
        world.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["cp", "-a", "--reflink=auto", str(SNAPSHOT / "minecraft/saves" / WORLD), str(world)], check=True)
    cfg = DST / "instance.cfg"
    text = cfg.read_text(encoding="utf-8")
    for key, value in {"name": NAME, "OverrideJavaArgs": "true", "JvmArgs": JVM_ARGS, "PreLaunchCommand": "",
                       "PostExitCommand": "", "WrapperCommand": "", "OverrideCommands": "true", "ShowConsole": "false",
                       "QuitAfterGameStop": "true"}.items():
        text = re.sub(rf"^{key}=.*$", f"{key}={value}", text, flags=re.M) if re.search(rf"^{key}=", text, re.M) \
            else text.replace("[General]\n", f"[General]\n{key}={value}\n", 1)
    cfg.write_text(text, encoding="utf-8")
    # Headless under software GL: shaders off, a modest view distance (the audit scans what the server loaded).
    iris = GAME / "config/iris.properties"
    if iris.exists():
        iris.write_text(re.sub(r"^enableShaders=.*$", "enableShaders=false", iris.read_text(encoding="utf-8"), flags=re.M), encoding="utf-8")
    options = GAME / "options.txt"
    if options.exists():
        text = options.read_text(encoding="utf-8")
        # Silent: the owner hears the test otherwise.
        for key, value in {"renderDistance": "8", "simulationDistance": "8", "pauseOnLostFocus": "false",
                           "soundCategory_master": "0.0", "soundCategory_music": "0.0"}.items():
            text = re.sub(rf"^{key}:.*$", f"{key}:{value}", text, flags=re.M)
        options.write_text(text, encoding="utf-8")


def install():
    for old in (GAME / "mods").glob("haven-consolidated*.jar"):
        old.unlink()
    shutil.copy2(jar(), GAME / "mods" / jar().name)
    (GAME / "haven-consolidated-audit.json").unlink(missing_ok=True)


def launch() -> subprocess.Popen:
    env = {k: v for k, v in os.environ.items() if k != "WAYLAND_DISPLAY"}
    env.update(QT_QPA_PLATFORM="xcb", LIBGL_ALWAYS_SOFTWARE="1")
    return subprocess.Popen(["xvfb-run", "-a", "-s", "-screen 0 1280x720x24", "prismlauncher", "--launch", NAME,
                             "--world", WORLD, "--offline", "Tester"], env=env, stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL, start_new_session=True)


def game_pids():
    """Java processes whose working folder is the test instance (never match by name: Prism and others run Java too)."""
    out = []
    for p in Path("/proc").iterdir():
        if not p.name.isdigit():
            continue
        try:
            if os.readlink(p / "cwd") == str(GAME):
                out.append(int(p.name))
        except OSError:
            pass
    return out


FATAL = ("Missing or unsupported mandatory dependencies", "Loading errors encountered", "Mixin apply failed",
         "failed to load mixin", "Mixin transformation of", "Haven Consolidated: creative tabs failed")


def kill_game(signal=15):
    for pid in game_pids():
        os.kill(pid, signal)
    subprocess.run(["pkill", "-f", f"prismlauncher --launch {NAME}"], check=False)


def wait(proc: subprocess.Popen) -> Path:
    """Until the audit file exists. A loading error leaves the game on an error screen for ever: the log is watched."""
    audit = GAME / "haven-consolidated-audit.json"
    log = GAME / "logs/latest.log"
    start = time.time()
    while time.time() - start < TIMEOUT:
        if audit.exists():
            break
        if log.exists():
            bad = [l for l in log.read_text(encoding="utf-8", errors="replace").splitlines() if any(m in l for m in FATAL)]
            if bad:
                kill_game()
                sys.exit("the game failed to load:\n  " + "\n  ".join(l[:300] for l in bad[:10]))
        if proc.poll() is not None and not game_pids():
            crash = sorted((GAME / "crash-reports").glob("*.txt")) if (GAME / "crash-reports").exists() else []
            sys.exit("the game ended before the audit" + (f"; crash report: {crash[-1]}" if crash else ""))
        time.sleep(10)
    else:
        kill_game()
        sys.exit(f"no audit after {TIMEOUT // 60} min")
    # The mod now leaves the world (saving it) and quits. A game still alive 90 s later is stuck: take a thread dump
    # with the instance's own JDK (jstack next to its java), then give it until 5 min before killing it.
    end = time.time() + 300
    dumped = False
    while time.time() < end and game_pids():
        if not dumped and time.time() > end - 210:
            dumped = True
            for pid in game_pids():
                dump = GAME / "haven-consolidated-threads.txt"
                with open(dump, "w") as out:
                    subprocess.run([str(jstack()), str(pid)], stdout=out, stderr=subprocess.STDOUT, check=False)
                print(f"game still running 90 s after the audit; thread dump in {dump}")
        time.sleep(5)
    for pid in game_pids():
        os.kill(pid, 9)
    return audit


def jstack() -> Path:
    cfg = (DST / "instance.cfg").read_text(encoding="utf-8")
    java = re.search(r"^JavaPath=(.*)$", cfg, re.M)
    return Path(java.group(1)).parent / "jstack" if java else Path("jstack")


def report(audit: Path):
    data = json.loads(audit.read_text(encoding="utf-8"))
    print("== audit ==")
    print(json.dumps(data["aliases"], indent=1))
    r = data["recipes"]
    print(f"recipes: {r['total']} total, old results: {r['old_item_results']}, ours: {r['our_outputs']}")
    if r["lookalike_groups"]:
        print("lookalike groups (same type, result, ingredients):")
        for k, ids in r["lookalike_groups"].items():
            print(f"  {k}: {ids}")
    print(f"world: {json.dumps(data['world'])}")
    log = GAME / "logs/latest.log"
    if log.exists():
        text = log.read_text(encoding="utf-8", errors="replace")
        print("== log ==")
        for line in text.splitlines():
            if "Haven Consolidated" in line or "haven_consolidated" in line.lower() and ("ERROR" in line or "WARN" in line) \
                    or "Mixin apply" in line and "failed" in line:
                print(" ", line[:300])
    print("== saved world ==")
    subprocess.run([sys.executable, "-I", str(REPO / "tools/world_scan.py"), str(GAME / "saves" / WORLD)], check=False)


def main():
    src = PRISM / "instances" / arg("--instance", "Haven Snapshot")
    skip = [sys.argv[i + 1] for i, a in enumerate(sys.argv) if a == "--skip" and i + 1 < len(sys.argv)]
    copy_instance(src, skip)
    install()
    proc = launch()
    print(f"launched {NAME} on {WORLD}; waiting for the audit (up to {TIMEOUT // 60} min)")
    audit = wait(proc)
    report(audit)
    if "--keep" not in sys.argv:
        shutil.rmtree(DST, ignore_errors=True)


if __name__ == "__main__":
    main()
