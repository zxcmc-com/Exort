package com.zxcmc.exort.infra.db;

public record DbItem(String key, byte[] blob, long amount) {}
