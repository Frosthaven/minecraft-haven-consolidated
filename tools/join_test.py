"""The test single player cannot run: a client joining a dedicated server, which syncs the registries over the network.

Needs a dedicated server with this build on localhost:25565 in offline mode, e.g. the Haven pack's
`just smoke --serve` with the jar copied into its mods cache and SMOKE_ONLINE_MODE=false. Then:

1. Prism instance "Haven Consolidated Join Test" = a copy of the pack instance "Haven" (no saves), this build's jar in
   mods/, no pre-launch command (the Haven launcher would update the pack), muted.
2. Launches it headless straight onto the server as the offline player "Tester".
3. Watches the client log: "Failed to handle registry sync" or a disconnect fails the test; the server's side is
   checked by the caller ("Tester joined the game" in the server log). Exits with the game closed.

Run: uv run python -I tools/join_test.py [--keep] [--instance Haven] [--server localhost:25565]
"""
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
PRISM = Path.home() / ".local/share/PrismLauncher"
NAME = "Haven Consolidated Join Test"
DST = PRISM / "instances" / NAME
GAME = DST / "minecraft"
SKIP = ["saves", "logs", "screenshots", "crash-reports", "journeymap", "schematics", "downloads", "server-resource-packs"]
TIMEOUT = 20 * 60
OK = "Haven Consolidated: audit in"   # the mod's own line once it runs on the server side (a joined client logs none)


def arg(flag, default):
    return sys.argv[sys.argv.index(flag) + 1] if flag in sys.argv else default


def jar() -> Path:
    version = re.search(r"^mod_version=(.*)$", (REPO / "gradle.properties").read_text(), re.M).group(1).strip()
    path = REPO / "build/libs" / f"haven-consolidated-{version}.jar"
    if not path.exists():
        sys.exit(f"{path} missing: run ./gradlew build")
    return path


def copy_instance(src: Path):
    if DST.exists():
        shutil.rmtree(DST)
    subprocess.run(["cp", "-a", "--reflink=auto", str(src), str(DST)], check=True)
    for rel in SKIP:
        shutil.rmtree(GAME / rel, ignore_errors=True)
    cfg = DST / "instance.cfg"
    text = cfg.read_text(encoding="utf-8")
    for key, value in {"name": NAME, "OverrideCommands": "true", "PreLaunchCommand": "", "PostExitCommand": "",
                       "WrapperCommand": "", "ShowConsole": "false", "QuitAfterGameStop": "true"}.items():
        text = re.sub(rf"^{key}=.*$", f"{key}={value}", text, flags=re.M) if re.search(rf"^{key}=", text, re.M) \
            else text.replace("[General]\n", f"[General]\n{key}={value}\n", 1)
    cfg.write_text(text, encoding="utf-8")
    iris = GAME / "config/iris.properties"
    if iris.exists():
        iris.write_text(re.sub(r"^enableShaders=.*$", "enableShaders=false", iris.read_text(encoding="utf-8"), flags=re.M), encoding="utf-8")
    options = GAME / "options.txt"
    if options.exists():
        text = options.read_text(encoding="utf-8")
        for key, value in {"renderDistance": "6", "pauseOnLostFocus": "false", "soundCategory_master": "0.0", "soundCategory_music": "0.0"}.items():
            text = re.sub(rf"^{key}:.*$", f"{key}:{value}", text, flags=re.M)
        options.write_text(text, encoding="utf-8")
    for old in (GAME / "mods").glob("haven-consolidated*.jar"):
        old.unlink()
    shutil.copy2(jar(), GAME / "mods" / jar().name)
    for i, a in enumerate(sys.argv):   # --extra-mod <jar>: a mod the server has that the instance lacks yet
        if a == "--extra-mod" and i + 1 < len(sys.argv):
            shutil.copy2(sys.argv[i + 1], GAME / "mods" / Path(sys.argv[i + 1]).name)


def game_pids():
    out = []
    for p in Path("/proc").iterdir():
        if p.name.isdigit():
            try:
                if os.readlink(p / "cwd") == str(GAME):
                    out.append(int(p.name))
            except OSError:
                pass
    return out


def stop():
    for pid in game_pids():
        os.kill(pid, 15)
    subprocess.run(["pkill", "-f", f"prismlauncher --launch {NAME}"], check=False)
    time.sleep(5)
    for pid in game_pids():
        os.kill(pid, 9)


def main():
    src = PRISM / "instances" / arg("--instance", "Haven")
    server = arg("--server", "localhost:25565")
    copy_instance(src)
    env = {k: v for k, v in os.environ.items() if k != "WAYLAND_DISPLAY"}
    env.update(QT_QPA_PLATFORM="xcb", LIBGL_ALWAYS_SOFTWARE="1")
    subprocess.Popen(["xvfb-run", "-a", "-s", "-screen 0 1280x720x24", "prismlauncher", "--launch", NAME,
                      "--server", server, "--offline", "Tester"], env=env, stdout=subprocess.DEVNULL,
                     stderr=subprocess.DEVNULL, start_new_session=True)
    print(f"launched {NAME} -> {server}; waiting for the join (up to {TIMEOUT // 60} min)")
    log = GAME / "logs/latest.log"
    start = time.time()
    verdict = None
    while time.time() - start < TIMEOUT and verdict is None:
        time.sleep(10)
        if not log.exists():
            continue
        text = log.read_text(encoding="utf-8", errors="replace")
        if "Failed to handle registry sync" in text or "Failed to sync registries" in text:
            verdict = "FAIL: registry sync"
        elif "ClientHooks.onRecipesUpdated" in text or re.search(r"Loaded \d+ advancements", text):
            verdict = "OK: joined (the play phase began: recipes received)"   # after the registry sync and login
        elif re.search(r"ClientPacketListener/\]: Disconnect|DisconnectedScreen|disconnect\.(lost|genericReason)|Failed to connect to server", text):
            verdict = "FAIL: disconnected"   # Sable's UDP "Connection refused" is not a disconnect
        elif "---- Minecraft Crash Report ----" in text:
            verdict = "FAIL: crash"
    if verdict is None:
        verdict = "FAIL: no join within the timeout"
    print(verdict)
    lines = log.read_text(encoding="utf-8", errors="replace").splitlines() if log.exists() else []
    for i, line in enumerate(lines):
        if "Haven Consolidated" in line or "Disconnected" in line:
            print("  ", line[:220])
        if "registry sync" in line:   # the stack trace is the evidence
            for l in lines[i:i + 16]:
                print("  ", l[:220])
    stop()
    if "--keep" not in sys.argv:
        shutil.rmtree(DST, ignore_errors=True)
    sys.exit(0 if verdict.startswith("OK") else 1)


if __name__ == "__main__":
    main()
