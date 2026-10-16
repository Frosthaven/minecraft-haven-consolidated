package dev.frosthaven.havenconsolidated;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeJsonTest {
    private static final Map<String, String> REWRITE = Map.of(
            "mekanism:ingot_lead", "haven_consolidated:lead_ingot",
            "tfmg:lead_ingot", "haven_consolidated:lead_ingot",
            "mekanism:nugget_lead", "haven_consolidated:lead_nugget");

    private static Map<String, JsonElement> recipes(String... idAndJson) {
        Map<String, JsonElement> m = new LinkedHashMap<>();
        for (int i = 0; i < idAndJson.length; i += 2) {
            m.put(idAndJson[i], JsonParser.parseString(idAndJson[i + 1]));
        }
        return m;
    }

    @Test
    void rewritesOldIdsWhereverTheyAreStrings() {
        Map<String, JsonElement> r = recipes("mekanism:x", """
                {"type":"minecraft:smelting","ingredient":{"item":"mekanism:nugget_lead"},
                 "result":{"id":"mekanism:ingot_lead","count":1},"tags":["mekanism:ingot_lead","other:thing"]}""");
        RecipeJson.Stats s = RecipeJson.consolidate(r, k -> k, REWRITE, "haven_consolidated:");
        assertEquals(3, s.rewritten());
        String text = r.get("mekanism:x").toString();
        assertTrue(text.contains("\"haven_consolidated:lead_nugget\""));
        assertTrue(text.contains("\"id\":\"haven_consolidated:lead_ingot\""));
        assertTrue(text.contains("\"other:thing\""));
        assertTrue(!text.contains("mekanism:"));
    }

    @Test
    void dropsCopiesThatSayTheSameThingAndKeepsOurs() {
        Map<String, JsonElement> r = recipes(
                "tfmg:lead_ingot_from_nuggets", """
                {"type":"minecraft:crafting_shaped","group":"tfmg","pattern":["NNN","NNN","NNN"],
                 "key":{"N":{"tag":"c:nuggets/lead"}},"result":{"item":"tfmg:lead_ingot","count":1}}""",
                "haven_consolidated:lead/ingot_from_nuggets", """
                {"type":"minecraft:crafting_shaped","category":"misc","pattern":["###","###","###"],
                 "key":{"#":{"tag":"c:nuggets/lead"}},"result":{"id":"haven_consolidated:lead_ingot"}}""",
                "mekanism:nugget_from_ingot", """
                {"type":"minecraft:crafting_shapeless","ingredients":[{"tag":"c:ingots/lead"}],
                 "result":{"id":"mekanism:nugget_lead","count":9}}""",
                "other:unrelated", """
                {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:stick"}],
                 "result":{"id":"minecraft:stick","count":2}}""");
        RecipeJson.Stats s = RecipeJson.consolidate(r, k -> k, REWRITE, "haven_consolidated:");
        assertEquals(List.of("tfmg:lead_ingot_from_nuggets = haven_consolidated:lead/ingot_from_nuggets"), s.removed());
        assertEquals(3, r.size());
        assertTrue(r.containsKey("haven_consolidated:lead/ingot_from_nuggets"));
        assertTrue(r.containsKey("mekanism:nugget_from_ingot"));
        assertTrue(r.containsKey("other:unrelated"));
    }

    @Test
    void keepsRecipesThatDifferByConditionsOrOrderOfShapedCells() {
        Map<String, JsonElement> r = recipes(
                "a:one", """
                {"type":"minecraft:smelting","ingredient":{"tag":"c:raw_materials/lead"},"result":{"id":"tfmg:lead_ingot"},
                 "experience":0.7,"cookingtime":200,"neoforge:conditions":[{"type":"neoforge:mod_loaded","modid":"x"}]}""",
                "b:two", """
                {"type":"minecraft:smelting","ingredient":{"tag":"c:raw_materials/lead"},"result":{"id":"mekanism:ingot_lead"},
                 "experience":0.7,"cookingtime":200}""",
                "c:shaped_a", """
                {"type":"minecraft:crafting_shaped","pattern":["AB"],"key":{"A":{"item":"mekanism:ingot_lead"},"B":{"item":"minecraft:stick"}},
                 "result":{"id":"minecraft:iron_sword"}}""",
                "d:shaped_b", """
                {"type":"minecraft:crafting_shaped","pattern":["BA"],"key":{"A":{"item":"mekanism:ingot_lead"},"B":{"item":"minecraft:stick"}},
                 "result":{"id":"minecraft:iron_sword"}}""");
        RecipeJson.Stats s = RecipeJson.consolidate(r, k -> k, REWRITE, "haven_consolidated:");
        assertEquals(List.of(), s.removed());
        assertEquals(4, r.size());
    }
}
