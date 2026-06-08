package com.zxcmc.exort.breaking;

import com.zxcmc.exort.carrier.Carriers;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class ClientBreakSpeedSuppressor {
  private final NamespacedKey modifierKey;
  private final AttributeModifier modifier;
  private final Function<Player, AttributeInstance> blockBreakSpeedAttribute;
  private final Set<UUID> suppressedPlayers = new HashSet<>();

  ClientBreakSpeedSuppressor(Plugin plugin) {
    this(plugin, player -> player.getAttribute(Attribute.BLOCK_BREAK_SPEED));
  }

  ClientBreakSpeedSuppressor(
      Plugin plugin, Function<Player, AttributeInstance> blockBreakSpeedAttribute) {
    this.modifierKey = new NamespacedKey(plugin, "custom_break_speed_suppression");
    this.modifier =
        new AttributeModifier(modifierKey, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    this.blockBreakSpeedAttribute = blockBreakSpeedAttribute;
  }

  static boolean shouldSuppress(Material wireMaterial, BreakType type) {
    return type == BreakType.WIRE && wireMaterial == Carriers.CHORUS_MATERIAL;
  }

  void apply(Player player) {
    if (player == null) return;
    AttributeInstance attribute = blockBreakSpeedAttribute.apply(player);
    if (attribute == null) return;
    if (attribute.getModifier(modifierKey) == null) {
      attribute.addTransientModifier(modifier);
    }
    suppressedPlayers.add(player.getUniqueId());
  }

  void clear(Player player) {
    if (player == null) return;
    clear(player.getUniqueId(), player);
  }

  void forget(UUID playerId) {
    if (playerId != null) {
      suppressedPlayers.remove(playerId);
    }
  }

  void clearAll() {
    for (UUID playerId : Set.copyOf(suppressedPlayers)) {
      clear(playerId, Bukkit.getPlayer(playerId));
    }
  }

  boolean isSuppressed(UUID playerId) {
    return suppressedPlayers.contains(playerId);
  }

  private void clear(UUID playerId, Player player) {
    if (player == null) {
      suppressedPlayers.remove(playerId);
      return;
    }
    AttributeInstance attribute = blockBreakSpeedAttribute.apply(player);
    if (attribute != null) {
      attribute.removeModifier(modifierKey);
    }
    suppressedPlayers.remove(playerId);
  }
}
