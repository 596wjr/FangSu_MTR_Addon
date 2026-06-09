package com.fangsu.ui;

import com.fangsu.mtr.LocalPlatform;
import com.fangsu.mtr.LocalRoute;
import mtr.data.Platform;

import java.util.Objects;

public final class RouteSelectInfo {

    public final LocalRoute route;
    public final Platform plat;
    public final LocalPlatform localPlatform;

    public RouteSelectInfo(LocalRoute route, Platform plat) {
        this.route = route;
        this.plat = plat;
        this.localPlatform = new LocalPlatform(plat);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (RouteSelectInfo) obj;
        return Objects.equals(this.route, that.route)
                && Objects.equals(this.plat, that.plat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(route, plat);
    }

    @Override
    public String toString() {
        return "RouteSelectInfo["
                + "route=" + route + ", "
                + "plat=" + plat + ']';
    }
}
