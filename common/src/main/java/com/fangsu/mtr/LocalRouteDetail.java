package com.fangsu.mtr;

import java.awt.*;
import java.util.List;
import java.util.Objects;

public class LocalRouteDetail {
    public final String routeName;
    public final Color routeColor;
    public final LocalRoute.CircularState circularState;
    public final int currentStationIndex;
    public final List<StationDetails> stationDetails;

    public final List<StationDetails> drawStations;

    public LocalRouteDetail(String routeName, int routeColor, LocalRoute.CircularState circularState, int currentStationIndex, List<StationDetails> stationDetails) {
        this.routeName = routeName;
        this.routeColor = new Color(routeColor);
        this.circularState = circularState;
        this.currentStationIndex = currentStationIndex;
        this.stationDetails = stationDetails;
        this.drawStations = this.stationDetails;
    }

    public static class StationDetails {
        public final String stationName;
        public final List<ColorNameTuple> transInfo;

        public StationDetails(String stationName, List<ColorNameTuple> transInfo) {
            this.stationName = stationName;
            this.transInfo = transInfo;
        }
    }

    public static class ColorNameTuple {

        public final Color color;
        public final String name;

        public ColorNameTuple(int color, String name) {
            this.color = new Color(color);
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ColorNameTuple o1) {
                return o1.color == color && o1.name.equals(name);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(color, color);
        }
    }
}
