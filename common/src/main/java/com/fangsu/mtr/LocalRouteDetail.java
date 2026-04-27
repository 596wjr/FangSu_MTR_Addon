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


}
