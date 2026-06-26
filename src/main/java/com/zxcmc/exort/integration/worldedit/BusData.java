package com.zxcmc.exort.integration.worldedit;

record BusData(String type, String facing, String mode, byte[] filters) implements FacingOwner {}
