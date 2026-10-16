# haven-consolidated (`haven_consolidated`)

NeoForge 1.21.1 mod, both sides, for the Haven pack (minecraft-haven-pack): one item per metal. Read README.md first.

## How a metal is replaced (`src/main/java/dev/frosthaven/havenconsolidated`)

Data: `consolidated/<metal>.json` and `consolidated/fluids/<fluid>.json` (hand-written: forms and the ids they
replace) -> `tools/gen.py` -> `src/main/resources/data/haven_consolidated/consolidated/` (resolved ids, read at
startup) plus the look, lang, `c:` tags, loot tables and the standard recipes (inputs by tag, results ours).
Everything under `assets/haven_consolidated` and `data/` is generated: edit the source file and rerun
`uv run --with pillow python -I tools/gen.py --mods <the pack's mods folder>` (or `HAVEN_MODS`), never the output.
Forms: ingot, nugget, raw, dust, gem (the bare item, e.g. Salt), sheet, rod, wire, ore, deepslate_ore, nether_ore,
moon_ore, basalt_ore, block, raw_block; `names` overrides a form's display name ("Niter", "Sulfur", "Salt Block"),
`ore_drop` names the form an ore drops (default raw). One ore form per host rock: an ore keeps the look of the rock it generates in, so a form holds
only sources that generate in that rock (Gunsmithing's sulfur ore sits in netherrack and More Features' in stone:
nether_ore and ore, not one). Check a new ore's placed feature targets before filing it.
Cogwheels are left out on purpose: they are Create kinetic blocks with behaviour, not plain items.

Fluids (`consolidated/fluids/<fluid>.json`: `fluid`, `name`, `kind` liquid|oil|gas|molten, `tags`, `replaces` = the
old still fluids): `Content.registerFluid` makes the still and flowing fluid, a FluidType per kind, the LiquidBlock
and the bucket, all under the fluid's id. `Aliases` aliases the old still fluid, its `flowing_` twin, every
LiquidBlock holding it and every BucketItem holding it (found by scanning the registries, so the mods' naming does
not matter). `FluidStackMixin` swaps an old fluid for ours in the constructor and makes `is(old)` true;
`FluidMixin.isSame` and `FluidStateMixin.is` do the same for fluids in the world. `client/ClientFluids` passes every
client extension call (textures, tint, fog) to the first old fluid present, so ours looks like the source mod's.
Mekanism's gases stay Mekanism's: those are chemicals, a separate registry; only its liquid forms are aliased.

The look: our item takes the source mod's own model files. The metal's `art` list ranks the source mods (Create:
Gunsmithing first by the owner's choice; TFMG, then Mekanism for the forms it lacks); the item model and, for blocks,
the blockstate of the first source that has them are copied as they are into our namespace, so textures and parent
models stay the source mod's and nothing of theirs is redistributed (Gunsmithing is All Rights Reserved). A form no
source provides gets a sprite from `tools/textures.py` (one base color per metal); that is the fallback only.

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
   result is one of our items or fluids, a recipe with the same type, results and ingredient sets as an earlier one
   is dropped (a mod's own-item input and the tag input resolve to the same set). Ours come first and win. Recipes
   for other things are never touched. Recipes that only differ in experience or cooking time count as the same.
   Fluid results and inputs are read by reflection from Create's `getFluidResults()` / `getFluidIngredients()`.
5. `AliasHits` (+ NeoForge's `ChunkDataEvent.Load`, posted at the end of each chunk read on the server thread): a
   chunk whose read hit an alias (old ore in a palette, old items in a container) is marked unsaved, so the next
   save writes our ids to the region file (the map and offline copies see the conversion). Reading explored areas
   therefore re-saves each chunk once. `ChunkEvent.Load` then checks every section's palette for an old block placed
   by code rather than by id (TFMG's oil wells and deposits set their fluid block object into new chunks) and
   rewrites those states to ours, keeping same-named properties (a fluid's level). The audit reports how many loaded
   chunks are unsaved, how many were marked and how many were rewritten.
6. `Audit`: `/consolidated audit`, or `-Dhaven_consolidated.audit=true` at server start (`.ticks`, `.quit`):
   `haven-consolidated-audit.json` in the game folder. `client/ClientAudit` then leaves the world the way the pause
   menu does (the integrated server saves every unsaved chunk) and quits; `Minecraft.stop()` alone exits without
   that save. On a dedicated server the marked chunks go out with the autosave or when they unload.

Consequences to keep in mind:
- The old items still exist in the registries (the owning mods reference them; they cannot be unregistered). They
  are unreachable: nothing resolves to them and no stack of them can be built. Creative tabs and JEI build stacks, so
  they show ours in their place (no hiding code needed). A tab that listed two old ids of one form (TFMG: lead ore
  and deepslate lead ore both became our ore once; three mods' ingots in the search tab; Mekanism's creative tank
  per fluid, four hydrogens now one) lists the same stack twice, which NeoForge rejects: `CreativeTabEventMixin`
  skips that check for every stack (the set add drops the duplicate; the check is a developer aid). The headless
  test builds every creative tab before it leaves the world, since opening the inventory in creative mode is where
  this crashed.
- A stack built through `ItemStack(ItemLike, int)` takes the old item's default components as its base; for the
  plain items we replace those are identical to ours.
- Two palette entries of one chunk section may decode to the same block; vanilla's palette copes (two ids, one state).
- A tag that listed three old ids and ours now holds our holder several times; harmless (JEI cycles the same item).
- Mekanism's processing intermediates (clump, shard, crystal, dirty dust) stay Mekanism's: stages, not duplicates.
- AE2 storage cells load entries with a plain put per key; two different old ids of one metal on the same cell would
  collapse to one key and keep the last count. The 2026-10-12 snapshot had no such cell (no cell held any old metal).

## Adding a metal

1. Survey: `python3 -I tools/survey.py <pack repo> <mods dir>...` lists every metal form more than one mod adds.
2. Write `consolidated/<metal>.json`: `metal`, `name`, `art` (source mod ids in the order their look is taken),
   `color` (hex, the fallback sprites' base), `tool` (stone|iron), and `forms` (ingot, nugget, raw, dust, sheet, ore,
   deepslate_ore, block, raw_block; each a list of old ids). A form with an empty list is still registered (ours
   only, sprite look).
3. `uv run --with pillow python -I tools/gen.py --mods <mods folder>`, `./gradlew build`, then the snapshot test.

## Testing a dedicated-server join (the test single player cannot run)

`tools/join_test.py [--extra-mod <jar>]`: a copy of the pack instance "Haven" (current mods, no saves, no pre-launch
command, muted) joins `localhost:25565` headless as the offline player "Tester" and the client log decides: the
registry sync failing, a disconnect or a crash fails; the play phase beginning (recipes received) passes. Start the
server first: the pack's smoke data (`minecraft-haven-pack/.cache/smoke/data`, offline mode) with this build's jar
swapped into `mods/`, run with the itzg image as `tools/join_test.py`'s docstring shows, or `just smoke --serve`.
Why it exists: 0.1.0 and 0.1.1 passed every single-player test and broke every server login. NeoForge's registry
sync builds the server's id table with `getId(name)` over every key; the alias answered our id for an old name, the
old entry's own id was never sent, and the client's id list kept a hole. `getId(name)` now runs with the alias off
(`MappedRegistryMixin`, `BaseMappedRegistryMixin`). Any change to the alias mechanism runs this test before release.

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
