# Consolidation queue

Every metal form more than one mod adds (tools/survey.py over the Haven pack, 2026-10-16), in the order to
do them. Lead is done. One consolidated/<metal>.json each, then the snapshot test. Queued, not started.

1 to 6 are whole chains: forms marked (dup) have several sources; the others are the single-source parts of
the same chain, owned too so the chain is one namespace. 7 on are single duplicated forms of metals whose
base is vanilla's or Create's (iron, copper, zinc...): only the listed form is ours, nothing else of that
metal moves. Art: Create: Gunsmithing first where it has the metal (steel), else the Create-family source.

## 1. nickel

- ingots (dup): tfmg:nickel_ingot, creatingspace:nickel_ingot
- nuggets (dup): tfmg:nickel_nugget, creatingspace:nickel_nugget
- raw_materials (dup): tfmg:raw_nickel, creatingspace:raw_nickel
- plates (dup): tfmg:nickel_sheet, creatingspace:nickel_sheet
- ores (dup): tfmg:deepslate_nickel_ore, tfmg:nickel_ore, creatingspace:deepslate_nickel_ore, creatingspace:moon_nickel_ore, creatingspace:nickel_ore, creatingspace:raw_nickel
- storage_blocks (dup): tfmg:nickel_block, creatingspace:nickel_block
- raw_storage_blocks (dup): tfmg:raw_nickel_block, creatingspace:raw_nickel_block

## 2. steel

- ingots (dup): ad_astra:steel_ingot, createbigcannons:steel_ingot, cgs:steel_ingot, tfmg:steel_ingot, mekanism:ingot_steel
- nuggets (dup): ad_astra:steel_nugget, createbigcannons:steel_scrap, cgs:steel_nugget, tfmg:steel_nugget, mekanism:nugget_steel
- dusts: mekanism:dust_steel
- plates (dup): ad_astra:steel_plate, cgs:steel_sheet, tfmg:heavy_plate
- gears: tfmg:steel_cogwheel
- rods (dup): ad_astra:steel_rod, tfmg:rebar
- storage_blocks (dup): ad_astra:steel_block, createbigcannons:steel_block, cgs:steel_block, tfmg:steel_block, mekanism:block_steel

## 3. aluminum

- ingots (dup): tfmg:aluminum_ingot, creatingspace:aluminum_ingot
- nuggets (dup): tfmg:aluminum_nugget, creatingspace:aluminum_nugget
- raw_materials: creatingspace:raw_aluminum
- plates (dup): tfmg:aluminum_sheet, creatingspace:aluminum_sheet
- gears: tfmg:aluminum_cogwheel
- wires: tfmg:aluminum_wire
- ores: creatingspace:moon_aluminum_ore
- storage_blocks (dup): tfmg:aluminum_block, creatingspace:aluminum_block
- raw_storage_blocks: creatingspace:raw_aluminum_block

## 4. bronze

- ingots (dup): createbigcannons:bronze_ingot, mekanism:ingot_bronze
- nuggets (dup): createbigcannons:bronze_scrap, mekanism:nugget_bronze
- dusts: mekanism:dust_bronze
- storage_blocks (dup): createbigcannons:bronze_block, mekanism:block_bronze

## 5. cast_iron

- ingots (dup): createbigcannons:cast_iron_ingot, tfmg:cast_iron_ingot
- nuggets (dup): createbigcannons:cast_iron_nugget, tfmg:cast_iron_nugget
- plates: tfmg:cast_iron_sheet
- storage_blocks (dup): createbigcannons:cast_iron_block, tfmg:cast_iron_block

## 6. sulfur

- dusts (dup): cgs:sulfur, tfmg:sulfur_dust, mekanism:dust_sulfur
- ores (dup): cgs:sulfur_ore, create_mf:sulfur_basalt_ore, create_mf:sulfur_ore

## 7. charcoal

- dusts (dup): cgs:charcoal_dust, mekanism:dust_charcoal

## 8. copper

- dusts (dup): createbb:crushed_copper, mekanism:dust_copper
- wires (dup): createaddition:copper_wire, electroenergetics:copper_wire, create_new_age:copper_wire, tfmg:copper_wire

## 9. diamond

- dusts (dup): createaddition:diamond_grit, mekanism:dust_diamond

## 10. electrum

- wires (dup): createaddition:electrum_wire, electroenergetics:electrum_wire

## 11. ender_pearl

- dusts (dup): ae2:ender_dust, garnished:crushed_ender_pearl

## 12. iron

- plates (dup): ad_astra:iron_plate, create:iron_sheet
- rods (dup): ad_astra:iron_rod, createaddition:iron_rod
- wires (dup): createaddition:iron_wire, electroenergetics:iron_wire

## 13. obsidian

- dusts (dup): create:powdered_obsidian, mekanism:dust_obsidian

## 14. saltpeter

- dusts (dup): cgs:niter, tfmg:nitrate_dust

## 15. wood

- dusts (dup): createdieselgenerators:wood_chip, mekanism:sawdust

## 16. zinc

- plates (dup): createaddition:zinc_sheet, createdeco:zinc_sheet
