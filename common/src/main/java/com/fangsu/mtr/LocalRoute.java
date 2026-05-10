package com.fangsu.mtr;

import com.fangsu.scripting.TextUtil;
import com.fangsu.utils.MtrUtil;
import mtr.data.Platform;
import mtr.data.Route;
import mtr.data.RouteType;
import mtr.data.Station;

import java.util.*;

public class LocalRoute {
    public RouteType routeType;
    public boolean isLightRailRoute;
    public boolean isHidden;
    public boolean disableNextStationAnnouncements;
    public CircularState circularState;
    public String lightRailRouteNumber;
    public final List<RoutePlatform> platformIds;
    public String name;
    public int color;
    public long id;

    private LocalRouteDetail cachedRouteDetail;

    public LocalRoute(Route route) {
        this.routeType = RouteType.valueOf(route.routeType.name());
        this.isLightRailRoute = route.isLightRailRoute;
        this.isHidden = route.isHidden;
        this.disableNextStationAnnouncements = route.disableNextStationAnnouncements;
        this.circularState = CircularState.valueOf(route.circularState.name());
        this.lightRailRouteNumber = route.lightRailRouteNumber;
        this.platformIds = new ArrayList<>();
        this.name = route.name;
        this.id = route.id;
        this.color = route.color;
        for (Route.RoutePlatform p : route.platformIds) {
            this.platformIds.add(new RoutePlatform(p.platformId, p.customDestination));
        }
    }

    public LocalRoute() {
        this.routeType = RouteType.NORMAL;
        this.isLightRailRoute = false;
        this.isHidden = false;
        this.disableNextStationAnnouncements = false;
        this.circularState = CircularState.NONE;
        this.lightRailRouteNumber = "";
        this.name = "?";
        this.id = 0L;
        this.color = 123456;
        this.platformIds = new ArrayList<>();
    }

    public static class RoutePlatform {
        public String customDestination;
        public final long platformId;

        public RoutePlatform(long platformId, String customDestination) {
            this.platformId = platformId;
            this.customDestination = customDestination;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof RoutePlatform o) {
                return platformId == o.platformId && customDestination.equals(o.customDestination);
            }
            return false;
        }
    }

    public int getPlatformIdIndex(long platformId) {
        for (int i = 0; i < platformIds.size(); i++) {
            if (platformIds.get(i).platformId == platformId) {
                return i;
            }
        }
        return -1;
    }

    public enum CircularState {
        NONE,
        CLOCKWISE,
        ANTICLOCKWISE;
    }

    public enum RouteType {
        NORMAL,
        LIGHT_RAIL,
        HIGH_SPEED;
    }

    public LocalRouteDetail asRouteDetail() {
        if (cachedRouteDetail != null) return cachedRouteDetail;
        List<LocalRouteDetail.StationDetails> stations = new ArrayList<>();
        for (RoutePlatform p : platformIds) {
            long platformId = p.platformId;
            Platform plat = MtrUtil.getPlatformById(platformId);
            if (plat != null) {
                Station stn = MtrUtil.getStationByPlatform(plat);
                if (stn != null) {
                    List<Platform> plats = MtrUtil.getPlatformByStation(stn);
                    Set<ColorNameTuple> trans = new HashSet<>();

                    if (plats != null) {
                        for (Platform plat1 : plats) {
                            List<LocalRoute> routes = MtrUtil.getRouteByPlatform(plat1);
                            if (routes != null) {
                                for (LocalRoute route : routes) {
                                    if (
                                            !(TextUtil.getCjkMatching(this.name, true).equals(TextUtil.getCjkMatching(route.name, true)) &&
                                                    TextUtil.getCjkMatching(this.name, false).equals(TextUtil.getCjkMatching(route.name, false)))
                                    )
                                        trans.add(new ColorNameTuple(route.color, TextUtil.getNonExtraParts(route.name)));
                                }
                            }
                        }
                        if (!trans.isEmpty())
                            stations.add(new LocalRouteDetail.StationDetails(stn.name, new ArrayList<>(trans)));
                        else stations.add(new LocalRouteDetail.StationDetails(stn.name, List.of()));
                    } else {
                        stations.add(new LocalRouteDetail.StationDetails(stn.name, null));
                    }
                } else {
                    stations.add(new LocalRouteDetail.StationDetails("未命名|Undefined", null));
                }
            }
        }
        LocalRouteDetail result = new LocalRouteDetail(name, color, circularState, 0, stations);
        cachedRouteDetail = result;
        return result;
    }
}
