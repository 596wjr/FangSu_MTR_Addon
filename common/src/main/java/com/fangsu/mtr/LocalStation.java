package com.fangsu.mtr;

import mtr.data.Station;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalStation extends LocalAreaBase {
    public int zone;
    public final Map<String, List<String>> exits;

    private final Station raw;

    public LocalStation(Station station) {
        super(
                station.id,
                station.name,
                station.color,
                station.corner1.getA(),
                station.corner1.getB(),
                station.corner2.getA(),
                station.corner2.getB()
        );
        this.raw = station;
        this.zone = station.zone;
        this.exits = new HashMap<>(station.exits);
    }

    public LocalStation() {
        super(
                0L, "?", 0x0, 0, 0, 0, 0
        );
        this.raw = null;
        this.zone = 0;
        this.exits = new HashMap<>();
    }

    public Station getRaw() {
        return raw;
    }
}
