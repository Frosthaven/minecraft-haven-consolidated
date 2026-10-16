"""Writes the mod's assets and data from consolidated/*.json (metals) and consolidated/fluids/*.json: each file names
what Haven Consolidated owns and the other mods' ids it replaces. Output (all committed, rewritten on every run):

- data/haven_consolidated/consolidated/<metal>.json, fluids/<fluid>.json, index.json: what the mod registers and
  aliases (read at startup);
- the look: our item uses the source mod's own model files (blockstate and item model copied as they are, so the
  textures and parents stay in the source mod's namespace and nothing is redistributed); a metal's `art` list says
  which mod to take first (Create: Gunsmithing by default), the others follow when it lacks a form. A fluid's bucket
  takes the first source's bucket model; its textures are delegated at runtime (client/ClientFluids). A form no mod
  provides gets a sprite from tools/textures.py;
- en_us lang, tags (c: forms, ores_in_ground, ore_rates, mineable, tool tiers, c: fluid tags), loot tables, the
  standard recipes (inputs by tag, results ours).

Run: uv run --with pillow python -I tools/gen.py [--mods <folder with the pack's mod jars>]
The mods folder (or $HAVEN_MODS) is where the source models are read from, e.g. a Prism instance's minecraft/mods.
"""
import json
import os
import shutil
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import textures  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
NS = "haven_consolidated"
RES = REPO / "src/main/resources"
ASSETS = RES / "assets" / NS
DATA = RES / "data"
DEFAULT_ART = ["cgs"]

# form -> (item id pattern, kind, lang pattern, c: tag folder)
FORMS = {
    "ingot": ("{m}_ingot", "item", "{n} Ingot", "ingots"),
    "nugget": ("{m}_nugget", "item", "{n} Nugget", "nuggets"),
    "raw": ("raw_{m}", "item", "Raw {n}", "raw_materials"),
    "dust": ("{m}_dust", "item", "{n} Dust", "dusts"),
    "sheet": ("{m}_sheet", "item", "{n} Sheet", "plates"),
    "rod": ("{m}_rod", "item", "{n} Rod", "rods"),
    "wire": ("{m}_wire", "item", "{n} Wire", "wires"),
    "ore": ("{m}_ore", "ore", "{n} Ore", "ores"),
    "deepslate_ore": ("deepslate_{m}_ore", "deepslate_ore", "Deepslate {n} Ore", "ores"),
    "nether_ore": ("nether_{m}_ore", "nether_ore", "Nether {n} Ore", "ores"),
    "moon_ore": ("moon_{m}_ore", "moon_ore", "Moon {n} Ore", "ores"),
    "basalt_ore": ("{m}_basalt_ore", "basalt_ore", "{n} Basalt Ore", "ores"),
    "block": ("{m}_block", "block", "Block of {n}", "storage_blocks"),
    "raw_block": ("raw_{m}_block", "raw_block", "Block of Raw {n}", "storage_blocks"),
}
# One form per host rock, so an ore keeps the look of the rock it sits in (an ore form holds only sources that
# generate in that rock: stone, deepslate, netherrack, moon stone, basalt).
ORE_KINDS = {"ore", "deepslate_ore", "nether_ore", "moon_ore", "basalt_ore"}
BLOCK_KINDS = ORE_KINDS | {"block", "raw_block"}
GROUND = {"ore": "stone", "deepslate_ore": "deepslate", "nether_ore": "netherrack"}


def write(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(data, indent=2) + "\n" if not isinstance(data, str) else data
    path.write_text(text, encoding="utf-8")


def tag(ns: str, kind: str, name: str, values: list):
    """Appends to a tag file (several metals share c:ingots etc.)."""
    path = DATA / ns / "tags" / kind / f"{name}.json"
    current = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
    for v in values:
        if v not in current["values"]:
            current["values"].append(v)
    write(path, current)


class Jars:
    """The pack's mod jars, indexed by asset path (first jar wins)."""

    def __init__(self, folder):
        self.index = {}
        if folder and Path(folder).is_dir():
            for jar in sorted(Path(folder).glob("*.jar")):
                try:
                    with zipfile.ZipFile(jar) as z:
                        for n in z.namelist():
                            if n.startswith("assets/") and n.endswith(".json"):
                                self.index.setdefault(n, jar)
                except zipfile.BadZipFile:
                    pass

    def json(self, path: str):
        jar = self.index.get(path)
        if not jar:
            return None
        with zipfile.ZipFile(jar) as z:
            return json.loads(z.read(path).decode("utf-8-sig"))


def single_variant(state: dict):
    """Our blocks have no properties: a source blockstate keyed by properties (a facing ore) or in multipart form is
    reduced to its first model as the one default variant; a plain one is kept as it is."""
    variants = state.get("variants")
    if variants is not None:
        if list(variants) == [""]:
            return state
        first = next(iter(variants.values()))
        return {"variants": {"": first}}
    parts = state.get("multipart", [])
    if parts:
        return {"variants": {"": parts[0]["apply"]}}
    return None


def source_art(old_ids: list, art: list, kind: str, jars: Jars):
    """(blockstate, item model, source id) from the first art mod that has the files, else None."""
    def rank(iid):
        ns = iid.split(":")[0]
        return art.index(ns) if ns in art else len(art)
    for old in sorted(old_ids, key=rank):
        ns, name = old.split(":", 1)
        model = jars.json(f"assets/{ns}/models/item/{name}.json")
        state = jars.json(f"assets/{ns}/blockstates/{name}.json") if kind in BLOCK_KINDS else None
        state = single_variant(state) if state else None
        if kind in BLOCK_KINDS and state:
            if not model:   # a block item without its own model file: the blockstate's model
                first = state["variants"][""]
                first = first[0] if isinstance(first, list) else first
                model = {"parent": first["model"]}
            return state, model, old
        elif kind not in BLOCK_KINDS and model:
            return None, model, old
    return None


def metal_ids(metal: dict) -> dict:
    """form -> haven_consolidated id for the forms this metal has."""
    return {f: f"{NS}:{FORMS[f][0].format(m=metal['metal'])}" for f in metal["forms"] if f in FORMS}


def look(form: str, kind: str, hc: str, old_ids: list, art: list, pal, jars: Jars) -> str:
    """Writes the model files for one of our items; returns where the art came from."""
    name = hc.split(":")[1]
    found = source_art(old_ids, art, kind, jars)
    if found:
        state, model, source = found
        if state:
            write(ASSETS / "blockstates" / f"{name}.json", state)
        write(ASSETS / "models" / "item" / f"{name}.json", model)
        return source
    sprite = form if form in textures.SPRITES else ("ore" if kind in ORE_KINDS else "ingot")
    sub = "block" if kind in BLOCK_KINDS else "item"
    png = ASSETS / "textures" / sub / f"{name}.png"
    png.parent.mkdir(parents=True, exist_ok=True)
    textures.SPRITES[sprite](pal).save(png)
    if kind in BLOCK_KINDS:
        write(ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(ASSETS / "models" / "block" / f"{name}.json",
              {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}})
        write(ASSETS / "models" / "item" / f"{name}.json", {"parent": f"{NS}:block/{name}"})
    else:
        write(ASSETS / "models" / "item" / f"{name}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
    return "sprite"


def gen_metal(metal: dict, lang: dict, index: list, jars: Jars):
    m, n = metal["metal"], metal["name"]
    ids = metal_ids(metal)
    pal = textures.palette(metal.get("color", "#8a8a8a"))
    art = metal.get("art", DEFAULT_ART)
    names = metal.get("names", {})
    tool = metal.get("tool", "stone")
    drop = ids.get(metal.get("ore_drop", "raw"))
    entries = []
    for form, hc in ids.items():
        pattern, kind, lang_pattern, folder = FORMS[form]
        name = hc.split(":")[1]
        source = look(form, kind, hc, metal["forms"][form], art, pal, jars)
        entries.append({"form": form, "kind": kind, "id": hc, "replaces": metal["forms"][form], "art": source})
        display = names.get(form, lang_pattern.format(n=n))
        if kind in BLOCK_KINDS:
            lang[f"block.{NS}.{name}"] = display
            if kind in ORE_KINDS:
                loot = {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [
                    {"type": "minecraft:alternatives", "children": [
                        {"type": "minecraft:item", "name": hc, "conditions": [{"condition": "minecraft:match_tool",
                            "predicate": {"predicates": {"minecraft:enchantments": [
                                {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
                        {"type": "minecraft:item", "name": drop or hc, "functions": [
                            {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                             "formula": "minecraft:ore_drops"},
                            {"function": "minecraft:explosion_decay"}]}]}]}],
                    "random_sequence": f"{NS}:blocks/{name}"}
            else:
                loot = {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                        "entries": [{"type": "minecraft:item", "name": hc}],
                        "conditions": [{"condition": "minecraft:survives_explosion"}]}],
                        "random_sequence": f"{NS}:blocks/{name}"}
            write(DATA / NS / "loot_table" / "blocks" / f"{name}.json", loot)
            tag("minecraft", "block", "mineable/pickaxe", [hc])
            tag("minecraft", "block", f"needs_{tool}_tool", [hc])
            if kind in ORE_KINDS:
                for reg in ("block", "item"):
                    tag("c", reg, f"ores/{m}", [hc])
                    tag("c", reg, "ores", [f"#c:ores/{m}"])
                    if kind in GROUND:
                        tag("c", reg, f"ores_in_ground/{GROUND[kind]}", [hc])
                    tag("c", reg, "ore_rates/singular", [hc])
            else:
                key = f"storage_blocks/{'raw_' if kind == 'raw_block' else ''}{m}"
                for reg in ("block", "item"):
                    tag("c", reg, key, [hc])
                    tag("c", reg, "storage_blocks", [f"#c:{key}"])
        else:
            lang[f"item.{NS}.{name}"] = display
            tag("c", "item", f"{folder}/{m}", [hc])
            tag("c", "item", folder, [f"#c:{folder}/{m}"])
    gen_recipes(m, ids)
    write(DATA / NS / "consolidated" / f"{m}.json", {"metal": m, "name": n, "entries": entries})
    index.append(m)
    print(f"{m}: " + ", ".join(f"{e['form']} <- {e['art']}" for e in entries))


def gen_fluid(fluid: dict, lang: dict, index: list, jars: Jars):
    f, n = fluid["fluid"], fluid["name"]
    ours, flowing, bucket = f"{NS}:{f}", f"{NS}:flowing_{f}", f"{NS}:{f}_bucket"
    source = "none"
    for old in fluid["replaces"]:   # the first source whose bucket model exists lends it to our bucket
        ns, name = old.split(":", 1)
        model = jars.json(f"assets/{ns}/models/item/{name}_bucket.json")
        if model:
            write(ASSETS / "models" / "item" / f"{f}_bucket.json", model)
            source = old
            break
    else:
        write(ASSETS / "models" / "item" / f"{f}_bucket.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/bucket"}})
    write(ASSETS / "blockstates" / f"{f}.json", {"variants": {"": {"model": "minecraft:block/water"}}})
    lang[f"fluid_type.{NS}.{f}"] = n
    lang[f"block.{NS}.{f}"] = n
    lang[f"item.{NS}.{f}_bucket"] = f"{n} Bucket"
    for t in fluid.get("tags", [f"c:{f}"]):
        ns, path = t.split(":", 1)
        tag(ns, "fluid", path, [ours, flowing])
    write(DATA / NS / "consolidated" / "fluids" / f"{f}.json",
          {"fluid": f, "name": n, "kind": fluid.get("kind", "liquid"), "id": ours, "flowing": flowing,
           "bucket": bucket, "replaces": fluid["replaces"], "art": source})
    index.append(f)
    print(f"fluid {f}: bucket <- {source}")


def recipe(name: str, data: dict):
    write(DATA / NS / "recipe" / name, data)


def gen_recipes(m: str, ids: dict):
    """The standard set, inputs by tag so every copy of a metal feeds them; results are ours."""
    ingot, nugget, raw, dust, sheet = (ids.get(k) for k in ("ingot", "nugget", "raw", "dust", "sheet"))
    block, raw_block = ids.get("block"), ids.get("raw_block")
    if ingot and block:
        recipe(f"{m}/block_from_ingots.json", {"type": "minecraft:crafting_shaped", "category": "misc",
               "pattern": ["###", "###", "###"], "key": {"#": {"tag": f"c:ingots/{m}"}}, "result": {"id": block}})
        recipe(f"{m}/ingot_from_block.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
               "ingredients": [{"tag": f"c:storage_blocks/{m}"}], "result": {"id": ingot, "count": 9}})
    if ingot and nugget:
        recipe(f"{m}/ingot_from_nuggets.json", {"type": "minecraft:crafting_shaped", "category": "misc",
               "pattern": ["###", "###", "###"], "key": {"#": {"tag": f"c:nuggets/{m}"}}, "result": {"id": ingot}})
        recipe(f"{m}/nugget_from_ingot.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
               "ingredients": [{"tag": f"c:ingots/{m}"}], "result": {"id": nugget, "count": 9}})
    if raw and raw_block:
        recipe(f"{m}/raw_block_from_raw.json", {"type": "minecraft:crafting_shaped", "category": "misc",
               "pattern": ["###", "###", "###"], "key": {"#": {"tag": f"c:raw_materials/{m}"}},
               "result": {"id": raw_block}})
        recipe(f"{m}/raw_from_raw_block.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
               "ingredients": [{"tag": f"c:storage_blocks/raw_{m}"}], "result": {"id": raw, "count": 9}})
    if ingot:
        sources = []
        if raw:
            sources.append(("raw", f"c:raw_materials/{m}", 0.7))
        if any(k in ids for k in ORE_KINDS):
            sources.append(("ore", f"c:ores/{m}", 0.7))
        if dust:
            sources.append(("dust", f"c:dusts/{m}", 0.3))
        for src, t, xp in sources:
            for kind, ticks in (("smelting", 200), ("blasting", 100)):
                recipe(f"{m}/ingot_from_{src}_{kind}.json", {"type": f"minecraft:{kind}", "category": "misc",
                       "ingredient": {"tag": t}, "result": {"id": ingot}, "experience": xp, "cookingtime": ticks})
    if ingot and sheet:
        recipe(f"{m}/sheet_pressing.json", {"type": "create:pressing", "ingredients": [{"tag": f"c:ingots/{m}"}],
               "results": [{"id": sheet}],
               "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "create"}]})


def main():
    mods = os.environ.get("HAVEN_MODS")
    if "--mods" in sys.argv:
        mods = sys.argv[sys.argv.index("--mods") + 1]
    jars = Jars(mods)
    if not jars.index:
        print("no mod jars found (--mods <folder> or HAVEN_MODS): every form gets a sprite", file=sys.stderr)
    for d in (ASSETS, DATA):
        shutil.rmtree(d, ignore_errors=True)
    lang = {f"itemGroup.{NS}": "Haven Consolidated"}
    metals, fluids = [], []
    for path in sorted((REPO / "consolidated").glob("*.json")):
        gen_metal(json.loads(path.read_text(encoding="utf-8")), lang, metals, jars)
    for path in sorted((REPO / "consolidated" / "fluids").glob("*.json")):
        gen_fluid(json.loads(path.read_text(encoding="utf-8")), lang, fluids, jars)
    write(ASSETS / "lang" / "en_us.json", dict(sorted(lang.items())))
    write(DATA / NS / "consolidated" / "index.json", {"metals": metals, "fluids": fluids})
    print(f"generated {len(metals)} metal(s) and {len(fluids)} fluid(s)")


if __name__ == "__main__":
    main()
