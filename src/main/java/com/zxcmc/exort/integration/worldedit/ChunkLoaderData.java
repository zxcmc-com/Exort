package com.zxcmc.exort.integration.worldedit;

import java.util.UUID;

record ChunkLoaderData(UUID id, UUID placedByUuid, String placedByName, long createdAt) {}
