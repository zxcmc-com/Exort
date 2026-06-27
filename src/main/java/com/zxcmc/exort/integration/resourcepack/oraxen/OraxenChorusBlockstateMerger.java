package com.zxcmc.exort.integration.resourcepack.oraxen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class OraxenChorusBlockstateMerger {
  static final String CHORUS_BLOCKSTATE_PATH = "assets/minecraft/blockstates/chorus_plant.json";

  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

  private OraxenChorusBlockstateMerger() {}

  static MergeResult merge(List<MergeInput> inputs) {
    Objects.requireNonNull(inputs, "inputs");
    if (inputs.size() <= 1) {
      return MergeResult.unchanged();
    }
    List<JsonObject> roots = new ArrayList<>(inputs.size());
    for (MergeInput input : inputs) {
      JsonObject root;
      try {
        JsonElement parsed = JsonParser.parseString(input.content());
        if (!parsed.isJsonObject()) {
          return MergeResult.failed(input.source() + " is not a JSON object");
        }
        root = parsed.getAsJsonObject();
      } catch (RuntimeException error) {
        return MergeResult.failed(input.source() + " is not valid JSON");
      }
      JsonElement variants = root.get("variants");
      if (variants == null || !variants.isJsonObject()) {
        return MergeResult.failed(input.source() + " has no object variants");
      }
      roots.add(root);
    }

    JsonObject mergedRoot = roots.get(0).deepCopy();
    JsonObject mergedVariants = new JsonObject();
    int conflicts = 0;
    for (JsonObject root : roots) {
      for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("variants").entrySet()) {
        JsonElement existing = mergedVariants.get(entry.getKey());
        if (existing == null) {
          mergedVariants.add(entry.getKey(), entry.getValue().deepCopy());
          continue;
        }
        if (!existing.equals(entry.getValue())) {
          conflicts++;
        }
      }
    }
    mergedRoot.add("variants", mergedVariants);
    return MergeResult.merged(GSON.toJson(mergedRoot), inputs.size(), conflicts);
  }

  record MergeInput(String source, String content) {}

  record MergeResult(
      boolean changed,
      boolean success,
      String content,
      int inputCount,
      int conflicts,
      String error) {
    static MergeResult unchanged() {
      return new MergeResult(false, true, null, 0, 0, null);
    }

    static MergeResult merged(String content, int inputCount, int conflicts) {
      return new MergeResult(true, true, content, inputCount, conflicts, null);
    }

    static MergeResult failed(String error) {
      return new MergeResult(false, false, null, 0, 0, error);
    }
  }
}
