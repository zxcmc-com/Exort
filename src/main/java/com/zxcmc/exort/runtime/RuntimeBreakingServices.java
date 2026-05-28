package com.zxcmc.exort.runtime;

import com.zxcmc.exort.breaking.BlockBreakHandler;
import com.zxcmc.exort.breaking.BreakSoundConfig;
import com.zxcmc.exort.breaking.CustomBlockBreaker;

public record RuntimeBreakingServices(
    BlockBreakHandler breakHandler,
    BreakSoundConfig breakSoundConfig,
    CustomBlockBreaker customBlockBreaker) {}
