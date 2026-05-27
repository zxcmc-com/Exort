package com.zxcmc.exort.placement;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ArmorStand.LockType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

public final class PaperEntityPlacementGuardBackend implements PlacementGuardBackend {
  private static final String GUARD_TAG = "exort_placement_guard";

  private final Plugin plugin;
  private final double guardScale;

  public PaperEntityPlacementGuardBackend(Plugin plugin, double guardScale) {
    this.plugin = plugin;
    this.guardScale = guardScale;
  }

  @Override
  public String name() {
    return "Paper entity";
  }

  @Override
  public boolean usesServerEntities() {
    return true;
  }

  @Override
  public PlacementGuardHandle createGuard(Player player, GuardTarget target) {
    World world = target.location().getWorld();
    if (world == null) return null;
    ArmorStand entity =
        world.spawn(
            target.location(),
            ArmorStand.class,
            guard -> {
              guard.setPersistent(false);
              guard.setInvulnerable(true);
              guard.setSilent(true);
              guard.setVisible(false);
              guard.setGravity(false);
              guard.setNoPhysics(true);
              guard.setVisibleByDefault(false);
              guard.setSmall(true);
              guard.setMarker(false);
              applyScale(guard);
              guard.setBasePlate(false);
              guard.setArms(false);
              guard.setAI(false);
              guard.setCanTick(false);
              guard.setCollidable(false);
              guard.setCanPickupItems(false);
              guard.setRemoveWhenFarAway(false);
              lockArmorStandEquipment(guard);
              guard.addScoreboardTag(GUARD_TAG);
            });
    player.showEntity(plugin, entity);
    return new PaperGuardHandle(entity);
  }

  private void applyScale(ArmorStand guard) {
    AttributeInstance scale = guard.getAttribute(Attribute.SCALE);
    if (scale != null) {
      scale.setBaseValue(guardScale);
    }
  }

  private void lockArmorStandEquipment(ArmorStand guard) {
    lockArmorStandEquipment(guard, EquipmentSlot.HAND);
    lockArmorStandEquipment(guard, EquipmentSlot.OFF_HAND);
    lockArmorStandEquipment(guard, EquipmentSlot.FEET);
    lockArmorStandEquipment(guard, EquipmentSlot.LEGS);
    lockArmorStandEquipment(guard, EquipmentSlot.CHEST);
    lockArmorStandEquipment(guard, EquipmentSlot.HEAD);
  }

  private void lockArmorStandEquipment(ArmorStand guard, EquipmentSlot slot) {
    guard.addEquipmentLock(slot, LockType.ADDING);
    guard.addEquipmentLock(slot, LockType.ADDING_OR_CHANGING);
    guard.addEquipmentLock(slot, LockType.REMOVING_OR_CHANGING);
  }

  private static final class PaperGuardHandle implements PlacementGuardHandle {
    private final ArmorStand entity;

    private PaperGuardHandle(ArmorStand entity) {
      this.entity = entity;
    }

    @Override
    public boolean isValid() {
      return entity.isValid();
    }

    @Override
    public void move(Player player, GuardTarget target) {
      Location location = target.location();
      if (location.getWorld() != null) {
        entity.teleport(location);
      }
    }

    @Override
    public void remove() {
      entity.remove();
    }

    @Override
    public UUID bukkitEntityUuid() {
      return entity.getUniqueId();
    }
  }
}
