package com.fangsu.mtr;

import mtr.data.Station;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalStation extends LocalAreaBase {
    public int zone;
    public final Map<String, List<String>> exits;

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
        this.zone = station.zone;
        this.exits = new HashMap<>(station.exits);
    }
}
