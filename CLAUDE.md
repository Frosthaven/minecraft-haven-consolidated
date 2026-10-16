# haven-consolidated (`haven_consolidated`)

NeoForge 1.21.1 mod, both sides, for the Haven pack (minecraft-haven-pack): one item per metal. Read README.md first.

## How a metal is replaced (`src/main/java/dev/frosthaven/havenconsolidated`)

Data: `consolidated/<metal>.json` (hand-written: forms and the ids they replace) -> `tools/gen.py` ->
`src/main/resources/data/haven_consolidated/consolidated/<metal>.json` (resolved ids, read at startup) plus textures
(`tools/textures.py`, one base color per metal), models, blockstates, lang, `c:` tags, loot tables and the standard
recipes (inputs by tag, results ours). Everything under `assets/haven_consolidated` and `data/` is generated: edit the
source file and rerun `uv run --with pillow python -I tools/gen.py`, never the output.

Runtime, in order:
1. `Consolidated.load()` reads the data files; `Content` registers an item (and block) per form and one creative tab.
2. `Aliases.activate()` (common setup, after every mod registered): for each old id that is registered, the item
   registry and the block registry get a forced alias to ours (`BaseMappedRegistryMixin`: NeoForge's own
   `addAlias` only answers for ids that are not registered, ours is consulted first in `resolve`, which every
   `get`/`getHolder` by name goes through). So stored stacks, recipe ingredients and results, loot entries, tag
   entries, worldgen block states and chunk palettes that name an old id all load as ours. The old item object also
   learns its target (`ItemMixin`, `Aliased`).
3. `ItemStackMixin`: the constructor every path ends in swaps an old item for ours (stacks built in code), and
   `stack.is(oldItem)` is true for our item (code asking "is this my ingot?" accepts ours).
4. `RecipeManagerMixin` -> `RecipeDedupe` (after parsing, so every id is already ours): among the recipes whose
   result is one of our items, a recipe with the same type, result and ingredient item sets as an earlier one is
   dropped (a mod's own-item input and the tag input resolve to the same set). Ours come first and win. Special
   recipes and recipes for other items are never touched. Recipes that only differ in experience or cooking time
   count as the same.
5. `AliasHits` (+ NeoForge's `ChunkDataEvent.Load`, posted at the end of each chunk read on the server thread): a
   chunk whose read hit an alias (old ore in a palette, old items in a container) is marked unsaved, so the next
   save writes our ids to the region file (the map and offline copies see the conversion). Reading explored areas
   therefore re-saves each chunk once. The audit reports how many loaded chunks are unsaved and how many were marked.
6. `Audit`: `/consolidated audit`, or `-Dhaven_consolidated.audit=true` at server start (`.ticks`, `.quit`):
   `haven-consolidated-audit.json` in the game folder. `client/ClientAudit` then leaves the world the way the pause
   menu does (the integrated server saves every unsaved chunk) and quits; `Minecraft.stop()` alone exits without
   that save. On a dedicated server the marked chunks go out with the autosave or when they unload.

Consequences to keep in mind:
- The old items still exist in the registries (the owning mods reference them; they cannot be unregistered). They
  are unreachable: nothing resolves to them and no stack of them can be built. Creative tabs and JEI build stacks, so
  they show ours in their place (no hiding code needed).
- A stack built through `ItemStack(ItemLike, int)` takes the old item's default components as its base; for the
  plain items we replace those are identical to ours.
- Two palette entries of one chunk section may decode to the same block; vanilla's palette copes (two ids, one state).
- A tag that listed three old ids and ours now holds our holder several times; harmless (JEI cycles the same item).
- Mekanism's processing intermediates (clump, shard, crystal, dirty dust) stay Mekanism's: stages, not duplicates.
- AE2 storage cells load entries with a plain put per key; two different old ids of one metal on the same cell would
  collapse to one key and keep the last count. The 2026-10-12 snapshot had no such cell (no cell held any old metal).

## Adding a metal

1. Survey: `python3 -I tools/survey.py <pack repo> <mods dir>...` lists every metal form more than one mod adds.
2. Write `consolidated/<metal>.json`: `metal`, `name`, `color` (hex, the sprites' base), `tool` (stone|iron), and
   `forms` (ingot, nugget, raw, dust, sheet, ore, deepslate_ore, block, raw_block; each a list of old ids). A form
   with an empty list is still registered (ours only).
3. `uv run --with pillow python -I tools/gen.py`, `./gradlew build`, then the snapshot test below.

## Testing against the Haven Snapshot world

`uv run --with nbtlib --with numpy python -I tools/snapshot_test.py [--keep]`: copies the Prism instance "Haven
Snapshot" (the pack client with the owner's downloaded world) to "Haven Consolidated Test", installs `build/libs`'s
jar, launches it headless (Xvfb, offline player) on the snapshot world with the audit flags, waits for the audit,
prints it with the mod's log lines, and runs `tools/world_scan.py` on the saved world (old ids left in the chunks the
session loaded: none). `tools/world_scan.py <world>` alone measures old stock in any world. Never match the game by
process name: the harness finds it by working folder.

## Release

Push to `main`: CI runs the tests and publishes the rolling `latest` release and an immutable `v<mod_version>-<sha7>`
release (the Haven pack pins one with `just bump-mod haven-consolidated`). Bump `mod_version` in `gradle.properties`.
