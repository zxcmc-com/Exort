package com.zxcmc.exort.wireless.charge;

import com.zxcmc.exort.core.keys.StorageKeys;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;

public final class WirelessChargeService {
    public static final int MAX_CHARGE = 100;
    public static final long FULL_CHARGE_MILLIS = 120_000L; // 2 minutes

    private final StorageKeys keys;

    public WirelessChargeService(StorageKeys keys) {
        this.keys = keys;
    }

    public void markStoredNow(PersistentDataContainer pdc) {
        pdc.set(keys.wirelessStoredAt(), PersistentDataType.LONG, Instant.now().toEpochMilli());
    }

    public void clearStoredAt(PersistentDataContainer pdc) {
        pdc.remove(keys.wirelessStoredAt());
    }

    public int computeCharge(PersistentDataContainer pdc) {
        long now = Instant.now().toEpochMilli();
        int charge = pdc.getOrDefault(keys.wirelessCharge(), PersistentDataType.INTEGER, MAX_CHARGE);
        long storedAt = pdc.getOrDefault(keys.wirelessStoredAt(), PersistentDataType.LONG, 0L);
        return computeChargeValue(now, charge, storedAt);
    }

    public boolean isCharging(PersistentDataContainer pdc) {
        long storedAt = pdc.getOrDefault(keys.wirelessStoredAt(), PersistentDataType.LONG, 0L);
        int charge = computeCharge(pdc);
        return storedAt > 0 && charge < MAX_CHARGE;
    }

    public long chargingEndsAtMillis(PersistentDataContainer pdc) {
        long storedAt = pdc.getOrDefault(keys.wirelessStoredAt(), PersistentDataType.LONG, 0L);
        if (storedAt <= 0) return -1L;
        int charge = computeCharge(pdc);
        if (charge >= MAX_CHARGE) {
            return System.currentTimeMillis();
        }
        double remaining = (MAX_CHARGE - charge) / (double) MAX_CHARGE;
        long remainingMs = (long) Math.ceil(remaining * FULL_CHARGE_MILLIS);
        return System.currentTimeMillis() + Math.max(0L, remainingMs);
    }

    public static int computeChargeValue(long now, int charge, long storedAt) {
        int clamped = Math.max(0, Math.min(MAX_CHARGE, charge));
        if (storedAt > 0 && clamped < MAX_CHARGE) {
            long elapsed = Math.max(0, now - storedAt);
            double gained = (elapsed / (double) FULL_CHARGE_MILLIS) * MAX_CHARGE;
            int newCharge = Math.min(MAX_CHARGE, (int) Math.round(clamped + gained));
            clamped = newCharge;
        }
        return Math.max(0, Math.min(MAX_CHARGE, clamped));
    }
}
