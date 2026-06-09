package com.fangsu.mtr;

import mtr.data.Platform;

public class LocalPlatform {
    private final Platform raw;
    public final long id;

    public LocalPlatform(Platform raw) {
        this.raw = raw;
        this.id = raw.id;
    }

    public LocalPlatform() {
        this.raw = null;
        this.id = 0L;
    }

    public Platform getRaw() {
        return raw;
    }
}
