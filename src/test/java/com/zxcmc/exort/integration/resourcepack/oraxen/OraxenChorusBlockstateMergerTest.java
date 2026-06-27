package com.zxcmc.exort.integration.resourcepack.oraxen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class OraxenChorusBlockstateMergerTest {
  @Test
  void mergesNonConflictingChorusVariants() {
    OraxenChorusBlockstateMerger.MergeResult result =
        OraxenChorusBlockstateMerger.merge(
            List.of(
                input(
                    "oraxen",
                    """
                    {"variants":{"east=false,north=false,south=false,west=false,up=false,down=false":{"model":"block/chorus_plant"}}}
                    """),
                input(
                    "exort",
                    """
                    {"variants":{"down=true,east=true,north=true,south=true,up=true,west=true":{"model":"exort:none"}}}
                    """)));

    assertTrue(result.success());
    assertTrue(result.changed());
    assertEquals(2, result.inputCount());
    assertEquals(0, result.conflicts());
    JsonObject variants = variants(result.content());
    assertEquals(
        "block/chorus_plant",
        variants
            .getAsJsonObject("east=false,north=false,south=false,west=false,up=false,down=false")
            .get("model")
            .getAsString());
    assertEquals(
        "exort:none",
        variants
            .getAsJsonObject("down=true,east=true,north=true,south=true,up=true,west=true")
            .get("model")
            .getAsString());
  }

  @Test
  void keepsFirstProviderVariantOnCollision() {
    OraxenChorusBlockstateMerger.MergeResult result =
        OraxenChorusBlockstateMerger.merge(
            List.of(
                input(
                    "oraxen",
                    """
                    {"variants":{"down=true,east=true,north=true,south=true,up=true,west=true":{"model":"oraxen:block"}}}
                    """),
                input(
                    "exort",
                    """
                    {"variants":{"down=true,east=true,north=true,south=true,up=true,west=true":{"model":"exort:none"}}}
                    """)));

    assertTrue(result.success());
    assertEquals(1, result.conflicts());
    assertEquals(
        "oraxen:block",
        variants(result.content())
            .getAsJsonObject("down=true,east=true,north=true,south=true,up=true,west=true")
            .get("model")
            .getAsString());
  }

  @Test
  void malformedDuplicateFailsWithoutProducingMergedContent() {
    OraxenChorusBlockstateMerger.MergeResult result =
        OraxenChorusBlockstateMerger.merge(
            List.of(
                input(
                    "oraxen",
                    """
                    {"variants":{"state":{"model":"oraxen:block"}}}
                    """),
                input("exort", "{not-json")));

    assertFalse(result.success());
    assertFalse(result.changed());
    assertEquals(null, result.content());
  }

  private static OraxenChorusBlockstateMerger.MergeInput input(String source, String content) {
    return new OraxenChorusBlockstateMerger.MergeInput(source, content);
  }

  private static JsonObject variants(String content) {
    return JsonParser.parseString(content).getAsJsonObject().getAsJsonObject("variants");
  }
}
