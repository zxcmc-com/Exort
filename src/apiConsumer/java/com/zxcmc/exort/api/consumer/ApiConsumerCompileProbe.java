package com.zxcmc.exort.api.consumer;

import com.zxcmc.exort.api.ExortApi;
import com.zxcmc.exort.api.model.ExortBlockDescriptor;
import com.zxcmc.exort.api.model.ExortContentType;
import com.zxcmc.exort.api.model.ExortItemCopyPolicy;
import com.zxcmc.exort.api.model.ExortItemDescriptor;
import com.zxcmc.exort.api.model.StorageTierDescriptor;
import java.util.Collection;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

final class ApiConsumerCompileProbe {
  private ApiConsumerCompileProbe() {}

  static boolean recognizesStorage(ExortApi api, Block block, ItemStack item) {
    Optional<ExortBlockDescriptor> blockDescriptor = api.inspectBlock(block);
    Optional<ExortItemDescriptor> itemDescriptor = api.inspectItem(item);
    Collection<StorageTierDescriptor> tiers = api.getStorageTiers();
    return api.getApiVersion() == 1
        && blockDescriptor.map(ExortBlockDescriptor::type).orElse(null) == ExortContentType.STORAGE
        && itemDescriptor.map(ExortItemDescriptor::copyPolicy).orElse(null)
            == ExortItemCopyPolicy.PRESERVE_UNIQUE_IDENTITY
        && itemDescriptor
            .flatMap(ExortItemDescriptor::variantKey)
            .flatMap(api::getStorageTier)
            .map(tiers::contains)
            .orElse(false);
  }
}
