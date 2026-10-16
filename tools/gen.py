"""Writes the mod's assets and data from consolidated/*.json: one file per metal names the forms Haven Consolidated
owns and the other mods' ids each one replaces. Output (all committed, rewritten from scratch on every run):

- data/haven_consolidated/consolidated/<metal>.json + index.json: what the mod registers and aliases (read at startup);
- textures (tools/textures.py), item and block models, blockstates, en_us lang;
- tags (c: forms, ores_in_ground, ore_rates, mineable, tool tiers), loot tables, the standard recipes.

Run: uv run --with pillow python -I tools/gen.py
"""
import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import textures  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
NS = "haven_consolidated"
RES = REPO / "src/main/resources"
ASSETS = RES / "assets" / NS
DATA = RES / "data"

# form -> (item id pattern, kind, lang pattern, c: tag folder)
FORMS = {
    "ingot": ("{m}_ingot", "item", "{n} Ingot", "ingots"),
    "nugget": ("{m}_nugget", "item", "{n} Nugget", "nuggets"),
    "raw": ("raw_{m}", "item", "Raw {n}", "raw_materials"),
    "dust": ("{m}_dust", "item", "{n} Dust", "dusts"),
    "sheet": ("{m}_sheet", "item", "{n} Sheet", "plates"),
    "ore": ("{m}_ore", "ore", "{n} Ore", "ores"),
    "deepslate_ore": ("deepslate_{m}_ore", "deepslate_ore", "Deepslate {n} Ore", "ores"),
    "block": ("{m}_block", "block", "Block of {n}", "storage_blocks"),
    "raw_block": ("raw_{m}_block", "raw_block", "Block of Raw {n}", "storage_blocks"),
}
BLOCK_KINDS = {"ore", "deepslate_ore", "block", "raw_block"}


def write(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(data, indent=2) + "\n" if not isinstance(data, str) else data
    path.write_text(text, encoding="utf-8")


def tag(ns: str, kind: str, name: str, values: list, replace=False):
    """Appends to a tag file (several metals share c:ingots etc.)."""
    path = DATA / ns / "tags" / kind / f"{name}.json"
    current = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": replace, "values": []}
    for v in values:
        if v not in current["values"]:
            current["values"].append(v)
    write(path, current)


def metal_ids(metal: dict) -> dict:
    """form -> haven_consolidated id for the forms this metal has."""
    return {f: f"{NS}:{FORMS[f][0].format(m=metal['metal'])}" for f in metal["forms"] if f in FORMS}


def gen_metal(metal: dict, lang: dict, index: list):
    m, n = metal["metal"], metal["name"]
    ids = metal_ids(metal)
    pal = textures.palette(metal["color"])
    entries = []
    tool = metal.get("tool", "stone")
    for form, hc in ids.items():
        pattern, kind, lang_pattern, folder = FORMS[form]
        name = hc.split(":")[1]
        entries.append({"form": form, "kind": kind, "id": hc, "replaces": metal["forms"][form]})
        # texture
        sub = "block" if kind in BLOCK_KINDS else "item"
        png = ASSETS / "textures" / sub / f"{name}.png"
        png.parent.mkdir(parents=True, exist_ok=True)
        textures.SPRITES[form](pal).save(png)
        if kind in BLOCK_KINDS:
            write(ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
            write(ASSETS / "models" / "block" / f"{name}.json",
                  {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}})
            write(ASSETS / "models" / "item" / f"{name}.json", {"parent": f"{NS}:block/{name}"})
            lang[f"block.{NS}.{name}"] = lang_pattern.format(n=n)
            # loot
            if kind in ("ore", "deepslate_ore"):
                drop = ids.get("raw", hc)
                loot = {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [
                    {"type": "minecraft:alternatives", "children": [
                        {"type": "minecraft:item", "name": hc, "conditions": [{"condition": "minecraft:match_tool",
                            "predicate": {"predicates": {"minecraft:enchantments": [
                                {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
                        {"type": "minecraft:item", "name": drop, "functions": [
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
            # block tags
            tag("minecraft", "block", "mineable/pickaxe", [hc])
            tag("minecraft", "block", f"needs_{tool}_tool", [hc])
            if kind in ("ore", "deepslate_ore"):
                tag("c", "block", f"ores/{m}", [hc])
                tag("c", "block", "ores", [f"#c:ores/{m}"])
                tag("c", "block", "ores_in_ground/" + ("deepslate" if kind == "deepslate_ore" else "stone"), [hc])
                tag("c", "block", "ore_rates/singular", [hc])
                tag("c", "item", f"ores/{m}", [hc])
                tag("c", "item", "ores", [f"#c:ores/{m}"])
                tag("c", "item", "ores_in_ground/" + ("deepslate" if kind == "deepslate_ore" else "stone"), [hc])
                tag("c", "item", "ore_rates/singular", [hc])
            else:
                key = f"storage_blocks/{'raw_' if kind == 'raw_block' else ''}{m}"
                tag("c", "block", key, [hc])
                tag("c", "block", "storage_blocks", [f"#c:{key}"])
                tag("c", "item", key, [hc])
                tag("c", "item", "storage_blocks", [f"#c:{key}"])
        else:
            write(ASSETS / "models" / "item" / f"{name}.json",
                  {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
            lang[f"item.{NS}.{name}"] = lang_pattern.format(n=n)
            tag("c", "item", f"{folder}/{m}", [hc])
            tag("c", "item", folder, [f"#c:{folder}/{m}"])
    gen_recipes(m, ids)
    write(DATA / NS / "consolidated" / f"{m}.json", {"metal": m, "name": n, "entries": entries})
    index.append(m)


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
        if "ore" in ids or "deepslate_ore" in ids:
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
    for d in (ASSETS, DATA):
        shutil.rmtree(d, ignore_errors=True)
    lang = {f"itemGroup.{NS}": "Haven Consolidated"}
    index = []
    for path in sorted((REPO / "consolidated").glob("*.json")):
        gen_metal(json.loads(path.read_text(encoding="utf-8")), lang, index)
    write(ASSETS / "lang" / "en_us.json", dict(sorted(lang.items())))
    write(DATA / NS / "consolidated" / "index.json", {"metals": index})
    print(f"generated {len(index)} metal(s): {', '.join(index)}")


if __name__ == "__main__":
    main()
