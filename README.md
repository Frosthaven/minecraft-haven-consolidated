# Haven Consolidated

One item per metal for the Haven server's modpack (Minecraft 1.21.1, NeoForge). Three mods ship a lead ingot, five
ship a steel ingot; after this mod there is one of each, Haven Consolidated's, and everything else in the game agrees:

- every recipe that made or took one of the old items makes or takes ours (recipe inputs that named an old item still
  work, because the old item *is* ours now);
- every old item already in the world becomes ours as it loads: chests, barrels, backpacks, drawers, item silos,
  AE2 cells, player inventories, dropped items;
- old ore blocks in the ground become our ore as their chunk loads, and the other mods' ore veins keep generating,
  now as our ore, so the world does not change shape;
- the old items are gone from creative tabs and JEI, and nothing can make one: a mod that builds a stack of its own
  ingot in code gets ours.

Required on the server and on every client. Nothing to configure.

## Download

**[Latest build](https://github.com/Frosthaven/minecraft-haven-consolidated/releases/latest)**: drop the jar into
`mods/` on the server and in each client instance. The Haven pack delivers it by itself (`mods/haven-consolidated.pw.toml`).

## What is consolidated

| Chain | Forms | Replaces |
|---|---|---|
| Lead | ingot, nugget, raw, dust, sheet, ore, deepslate ore, block, raw block | Mekanism, Create: The Factory Must Grow, Create: Gunsmithing |
| Nickel | ingot, nugget, raw, sheet, ore, deepslate ore, moon ore, block, raw block | TFMG, Creating Space |
| Steel | ingot, nugget, dust, sheet, rod, block | Gunsmithing, TFMG, Create Big Cannons, Ad Astra, Mekanism |
| Aluminum | ingot, nugget, raw, sheet, wire, moon ore, block, raw block | TFMG, Creating Space |
| Bronze | ingot, nugget, dust, block | Create Big Cannons, Mekanism |
| Cast iron | ingot, nugget, sheet, block | Create Big Cannons, TFMG |
| Sulfur | sulfur, ore, nether ore, basalt ore | Gunsmithing, TFMG, Create: More Features, Mekanism |
| Lithium | ingot, nugget, raw, dust, ore, deepslate ore, block | TFMG, Create: Solarpunk, Mekanism |
| Salt | salt, salt block | Mekanism, Create: Solarpunk |
| Single forms | charcoal dust, copper dust, copper wire, diamond dust, electrum wire, ender dust, iron sheet, iron rod, iron wire, powdered obsidian, niter, sawdust, zinc sheet | the mods that duplicated each |
| Fluids | crude oil, diesel, gasoline, plant oil, bioethanol (also Solarpunk's biofuel), dragon's breath, molten steel, hydrogen, oxygen, steam, deuterium, fertilizer | TFMG, Create Diesel Generators, Ad Astra, Crafts & Additions, Electro Energetics, Mekanism, Mekanism Generators, Create Big Cannons, Dragons Plus, Garnished, Deep Seas, Broken Bad, Create: Solarpunk, Slice & Dice |

`consolidated/<metal>.json` and `consolidated/fluids/<fluid>.json` list what each one replaces; ids of mods that are
not installed are skipped, so the same jar works in any pack. Our items look like the source mods' own (Create:
Gunsmithing first, then the others for the forms it lacks): they use those mods' model files, and our fluids borrow
the source fluid's textures at runtime, so the art stays theirs.
`uv run --with pillow python -I tools/gen.py --mods <mods folder>` turns the data files into the mod's items,
models, tags, loot tables and recipes (all committed). See CLAUDE.md for how the replacement works and how to add
one. Mekanism's gases are chemicals, a separate system, and stay as they are; only their liquid forms are
consolidated.

## Commands

`/consolidated audit` (ops) writes `haven-consolidated-audit.json` into the game folder: every old id and what it
resolves to, recipes that still produce an old item (none, when it works), recipes that look like duplicates of each
other, and the old and new blocks and items in the chunks around players.

## Development

`./gradlew build` (Gradle on JDK 17 or 21, mod on Java 21). The test that matters is
`tools/snapshot_test.py`: it runs the built jar against the Haven Snapshot world headless and checks the audit; see
CLAUDE.md. Every push to `main` publishes a rolling `latest` release and an immutable `v<version>-<commit>` release,
which the Haven pack pins by hash (`just bump-mod haven-consolidated` in minecraft-haven-pack).
