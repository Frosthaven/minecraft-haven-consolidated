"""Survey: which mods of a packwiz pack add the same metal in the same form (what consolidated/*.json should list).

usage: python3 -I tools/survey.py <pack repo> <mods dir> [<mods dir> ...]

Reads the pack's mods/*.pw.toml (file names, sides) and the four committed jars, finds each jar in the mods dirs,
and scans the jars (and the jars nested in them) for item models, en_us names and item tags. Forms come from the
common `c:` tags (c:ingots/<metal>, c:nuggets/<metal>, c:raw_materials/<metal>, c:dusts/<metal>, c:plates/<metal>,
c:gears/<metal>, c:rods/<metal>, c:wires/<metal>, c:ores/<metal>, c:storage_blocks/<metal>, c:storage_blocks/raw_<metal>)
plus a name pass over untagged item ids. Prints a Markdown report.
"""
import io
import json
import re
import sys
import tomllib
import zipfile
from collections import defaultdict
from pathlib import Path

FORMS = ["ingots", "nuggets", "raw_materials", "dusts", "plates", "gears", "rods", "wires", "ores",
         "storage_blocks", "raw_storage_blocks"]
# name pass: <metal>_<suffix> or <prefix>_<metal>
NAME_FORMS = {"ingots": ["ingot"], "nuggets": ["nugget"], "raw_materials": ["raw"], "dusts": ["dust"],
              "plates": ["plate", "sheet"], "gears": ["gear", "cogwheel"], "rods": ["rod"], "wires": ["wire"],
              "ores": ["ore"], "storage_blocks": ["block"], "raw_storage_blocks": ["raw_block", "block_raw"]}
ORE_VARIANTS = ("deepslate", "nether", "end", "stone", "sandstone", "granite", "diorite", "andesite", "tuff",
                "blackstone", "basalt", "moon", "mars", "venus", "mercury", "glacio", "sand", "gravel", "crushed",
                "small", "poor", "dense", "rich", "cluster")


class Jar:
    def __init__(self, name, side):
        self.name, self.side = name, side
        self.mod_ids, self.display = [], {}


items = {}                 # item id -> jar name (first seen)
lang = {}                  # item id -> en_us name
tags = defaultdict(list)   # tag id -> [(value, required)]
tag_sources = defaultdict(set)  # tag id -> {jar names}
block_models = set()
jars = {}                  # jar name -> Jar
ns_to_mod = {}             # namespace -> display name


def read_mods_toml(zf, jar):
    for cand in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
        if cand in zf.namelist():
            text = zf.read(cand).decode("utf-8", "replace")
            try:
                data = tomllib.loads(text)
                for m in data.get("mods", []):
                    jar.mod_ids.append(m["modId"])
                    jar.display[m["modId"]] = m.get("displayName", m["modId"])
            except Exception:
                for mid, disp in re.findall(r'modId\s*=\s*"([^"]+)"[\s\S]*?displayName\s*=\s*"([^"]+)"', text):
                    jar.mod_ids.append(mid)
                    jar.display[mid] = disp
            return


def scan_zip(zf, jar, depth):
    read_mods_toml(zf, jar)
    for n in zf.namelist():
        parts = n.split("/")
        if parts[0] == "assets" and len(parts) >= 5 and parts[2] == "models" and parts[3] == "item" and n.endswith(".json"):
            iid = f"{parts[1]}:{'/'.join(parts[4:])[:-5]}"
            items.setdefault(iid, jar.name)
        elif parts[0] == "assets" and len(parts) >= 5 and parts[2] == "models" and parts[3] == "block" and n.endswith(".json"):
            block_models.add(f"{parts[1]}:{'/'.join(parts[4:])[:-5]}")
        elif parts[0] == "assets" and len(parts) == 4 and parts[2] == "lang" and parts[3] == "en_us.json":
            try:
                data = json.loads(zf.read(n).decode("utf-8-sig", "replace"))
            except Exception:
                continue
            for k, v in data.items():
                if k.startswith(("item.", "block.")):
                    bits = k.split(".")
                    if len(bits) >= 3:
                        lang.setdefault(f"{bits[1]}:{'.'.join(bits[2:])}", v)
        elif parts[0] == "data" and len(parts) >= 5 and parts[2] == "tags" and parts[3] in ("item", "items") and n.endswith(".json"):
            tid = f"{parts[1]}:{'/'.join(parts[4:])[:-5]}"
            try:
                data = json.loads(zf.read(n).decode("utf-8-sig", "replace"))
            except Exception:
                continue
            tag_sources[tid].add(jar.name)
            for v in data.get("values", []):
                if isinstance(v, dict):
                    tags[tid].append((v.get("id", ""), v.get("required", True)))
                else:
                    tags[tid].append((v, True))
        elif n == "META-INF/jarjar/metadata.json" and depth < 3:
            try:
                meta = json.loads(zf.read(n))
                for e in meta.get("jars", []):
                    p = e.get("path")
                    if p and p in zf.namelist():
                        with zipfile.ZipFile(io.BytesIO(zf.read(p))) as inner:
                            scan_zip(inner, jar, depth + 1)
            except Exception as e:
                print(f"<!-- nested jar read failed in {jar.name}: {e} -->", file=sys.stderr)


def resolve(tid, seen=None):
    seen = seen or set()
    if tid in seen:
        return set()
    seen.add(tid)
    out = set()
    for v, req in tags.get(tid, []):
        if v.startswith("#"):
            out |= resolve(v[1:], seen)
        else:
            out.add(v)
    return out


def mod_of(iid):
    ns = iid.split(":")[0]
    return ns_to_mod.get(ns, ns)


def main():
    pack, dirs = Path(sys.argv[1]), [Path(d) for d in sys.argv[2:]]
    wanted = {}
    for meta in sorted((pack / "mods").glob("*.pw.toml")):
        d = tomllib.loads(meta.read_text(encoding="utf-8"))
        wanted[d["filename"]] = d.get("side", "both")
    for j in sorted((pack / "mods").glob("*.jar")):
        wanted[j.name] = "both"
    missing = []
    for fname, side in wanted.items():
        path = next((d / fname for d in dirs if (d / fname).exists()), None)
        if not path:
            missing.append(fname)
            continue
        jar = Jar(fname, side)
        jars[fname] = jar
        try:
            with zipfile.ZipFile(path) as zf:
                scan_zip(zf, jar, 0)
        except zipfile.BadZipFile:
            missing.append(fname + " (bad zip)")
    for jar in jars.values():
        for mid in jar.mod_ids:
            ns_to_mod.setdefault(mid, jar.display.get(mid, mid))

    # metals from the c: tags
    metals = defaultdict(lambda: defaultdict(set))   # metal -> form -> item ids
    for tid in tags:
        ns, path = tid.split(":", 1)
        if ns != "c":
            continue
        bits = path.split("/")
        if len(bits) != 2:
            continue
        form, metal = bits
        if form == "storage_blocks" and metal.startswith("raw_"):
            form, metal = "raw_storage_blocks", metal[4:]
        if form not in FORMS:
            continue
        for iid in resolve(tid):
            if iid in items or iid.split(":")[0] in ns_to_mod:
                metals[metal][form].add(iid)
    # name pass over every item id, only for metals the tags already named (keeps it to metals)
    known = set(metals)
    for iid in items:
        ns, name = iid.split(":", 1)
        base = name.rsplit("/", 1)[-1]
        for form, words in NAME_FORMS.items():
            for w in words:
                for metal in known:
                    if base in (f"{metal}_{w}", f"{w}_{metal}") or \
                            (form == "ores" and re.fullmatch(rf"({'|'.join(ORE_VARIANTS)})_{metal}_ore|{metal}_({'|'.join(ORE_VARIANTS)})_ore|{metal}_ore_({'|'.join(ORE_VARIANTS)})", base)):
                        if iid not in metals[metal][form]:
                            metals[metal][form].add(iid + "  (untagged)")

    def by_mod(ids):
        groups = defaultdict(list)
        for iid in sorted(ids):
            groups[mod_of(iid.split()[0])].append(iid)
        return groups

    print("# Duplicated metals in the Haven pack\n")
    print(f"Jars scanned: {len(jars)} of {len(wanted)} in the pack. Mods with ids: {len(ns_to_mod)}.")
    if missing:
        print(f"\nNot found locally ({len(missing)}): " + ", ".join(missing))
    # summary of duplicates
    dups = []
    for metal, forms in metals.items():
        for form, ids in forms.items():
            mods = by_mod(ids)
            if len(mods) > 1:
                dups.append((len(mods), metal, form, mods))
    dups.sort(key=lambda d: (-d[0], d[1], FORMS.index(d[2])))
    print("\n## Duplicates (a metal form that more than one mod adds)\n")
    print("| Metal | Form | Mods | Items |\n|---|---|---|---|")
    for n, metal, form, mods in dups:
        cells = "; ".join(f"**{m}**: " + ", ".join(i.replace("  (untagged)", "*") for i in ids) for m, ids in sorted(mods.items()))
        print(f"| {metal} | {form} | {n} | {cells} |")
    print("\n`*` = found by name only (not in the c: tag for that form).")
    print("\n## Every metal, every form\n")
    for metal in sorted(metals):
        forms = metals[metal]
        print(f"\n### {metal}\n")
        print("| Form | Items by mod |\n|---|---|")
        for form in FORMS:
            if form in forms:
                mods = by_mod(forms[form])
                cells = "; ".join(f"**{m}**: " + ", ".join(i.replace("  (untagged)", "*") + (f" ({lang[i.split()[0]]})" if i.split()[0] in lang else "") for i in ids) for m, ids in sorted(mods.items()))
                print(f"| {form}{' (DUP)' if len(mods) > 1 else ''} | {cells} |")
    print("\n## Tag files for lead (which jars write them)\n")
    for tid in sorted(t for t in tags if t.startswith("c:") and t.endswith("/lead") or t.endswith("/raw_lead")):
        print(f"- `{tid}`: " + ", ".join(sorted(tag_sources[tid])) + " -> " + ", ".join(sorted(resolve(tid))))


if __name__ == "__main__":
    main()
