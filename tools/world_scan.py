"""Counts the old ids and ours in a saved world: item stacks anywhere (containers, nested items, players, entities,
data/*.dat) and blocks in chunk palettes. Before the mod: how much old stock exists. After a session with the mod:
the chunks that were loaded and saved hold none of the old ids.

usage: uv run --with nbtlib --with numpy python -I tools/world_scan.py <world> [out.md]
Targets come from consolidated/*.json (every "replaces" id and every Haven Consolidated id).
"""
import gzip
import io
import json
import sys
import zlib
from collections import Counter, defaultdict
from pathlib import Path

import nbtlib
import numpy as np

REPO = Path(__file__).resolve().parent.parent
GENERATED = REPO / "src/main/resources/data/haven_consolidated/consolidated"
COUNT_KEYS = ("count", "Count", "#c", "amount", "Amount")


def targets():
    """id -> (metal, form, 'old'|'ours'), from the generated data files (tools/gen.py resolves the ids)."""
    out = {}
    for path in sorted(GENERATED.glob("*.json")):
        if path.name == "index.json":
            continue
        metal = json.loads(path.read_text(encoding="utf-8"))
        for entry in metal["entries"]:
            out[entry["id"]] = (metal["metal"], entry["form"], "ours")
            for old in entry["replaces"]:
                out[old] = (metal["metal"], entry["form"], "old")
    return out


def region_chunks(path: Path):
    data = path.read_bytes()
    for i in range(1024):
        off = int.from_bytes(data[i * 4:i * 4 + 3], "big") * 4096
        if off == 0 or off + 5 > len(data):
            continue
        length = int.from_bytes(data[off:off + 4], "big")
        comp = data[off + 4]
        raw = data[off + 5:off + 4 + length]
        try:
            if comp == 2:
                raw = zlib.decompress(raw)
            elif comp == 1:
                raw = gzip.decompress(raw)
            elif comp != 3:
                continue
        except Exception:
            continue
        yield raw


class Scan:
    def __init__(self, ids):
        self.ids = ids
        self.words = sorted({t.split(":")[0].encode() for t in ids} | {m.encode() for m, _, _ in ids.values()})
        self.items = Counter()
        self.where = defaultdict(Counter)
        self.blocks = Counter()
        self.chunks = 0

    def walk(self, tag, container):
        if isinstance(tag, nbtlib.Compound):
            if isinstance(tag.get("id"), nbtlib.String) and ":" in str(tag["id"]):
                iid = str(tag["id"])
                if iid in self.ids:
                    n = 1
                    for k in COUNT_KEYS:
                        if k in tag:
                            try:
                                n = int(tag[k])
                            except Exception:
                                n = 1
                            break
                    self.items[iid] += n
                    self.where[iid][container] += n
                container = iid
            for v in tag.values():
                self.walk(v, container)
        elif isinstance(tag, nbtlib.List):
            for v in tag:
                self.walk(v, container)

    def chunk(self, raw, kind):
        self.chunks += 1
        if not any(w in raw for w in self.words):
            return
        root = nbtlib.File.parse(io.BytesIO(raw))
        if kind == "entities":
            self.walk(root.get("Entities", []), "entity")
            return
        self.walk(root.get("block_entities", []), "block_entity")
        for sec in root.get("sections", []):
            bs = sec.get("block_states")
            if not bs:
                continue
            palette = [str(p["Name"]) for p in bs.get("palette", [])]
            hits = [i for i, name in enumerate(palette) if name in self.ids]
            if not hits:
                continue
            if "data" not in bs or len(palette) == 1:
                for i in hits:
                    self.blocks[palette[i]] += 4096
                continue
            bits = max(4, (len(palette) - 1).bit_length())
            arr = np.asarray(bs["data"], dtype=np.int64).view(np.uint64)
            per = 64 // bits
            mask = np.uint64((1 << bits) - 1)
            idx = np.stack([(arr >> np.uint64(s * bits)) & mask for s in range(per)], axis=1).reshape(-1)[:4096]
            counts = np.bincount(idx.astype(np.int64), minlength=len(palette))
            for i in hits:
                self.blocks[palette[i]] += int(counts[i])


def main():
    world = Path(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else None
    ids = targets()
    scan = Scan(ids)
    for mca in sorted(world.rglob("*.mca")):
        kind = mca.parent.name
        if kind not in ("region", "entities"):
            continue
        for raw in region_chunks(mca):
            try:
                scan.chunk(raw, kind)
            except Exception as e:
                print(f"<!-- {mca}: {e} -->", file=sys.stderr)
    for folder in ("playerdata", "data"):
        for dat in sorted((world / folder).glob("*.dat")):
            try:
                scan.walk(nbtlib.load(str(dat)), folder)
            except Exception as e:
                print(f"<!-- {dat.name}: {e} -->", file=sys.stderr)
    lines = [f"# {world.name}: old ids and ours\n", f"Chunks: {scan.chunks}\n",
             "| Item | Metal / form | Old or ours | Count | Where |", "|---|---|---|---|---|"]
    for iid, n in sorted(scan.items.items(), key=lambda kv: (ids[kv[0]][:2], ids[kv[0]][2], kv[0])):
        m, f, side = ids[iid]
        lines.append(f"| {iid} | {m} / {f} | {side} | {n} | " + ", ".join(f"{c} ({k})" for c, k in scan.where[iid].most_common(4)) + " |")
    lines += ["\n| Block | Old or ours | Blocks |", "|---|---|---|"]
    for bid, n in sorted(scan.blocks.items(), key=lambda kv: (ids[kv[0]][2], kv[0])):
        lines.append(f"| {bid} | {ids[bid][2]} | {n} |")
    old_items = sum(n for i, n in scan.items.items() if ids[i][2] == "old")
    old_blocks = sum(n for b, n in scan.blocks.items() if ids[b][2] == "old")
    lines.append(f"\nOld items: {old_items}. Old blocks: {old_blocks}.")
    text = "\n".join(lines) + "\n"
    if out:
        out.write_text(text, encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
