package com.fangsu.extraConfig;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

public abstract class Config {
    public abstract AbstractConfigListEntry getEntry(ConfigEntryBuilder builder);
}
