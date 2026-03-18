package com.fangsu.ui;

import com.fangsu.mtr.LocalRoute;
import com.fangsu.utils.ColorUtil;
import com.fangsu.utils.MtrUtil;
import mtr.data.Platform;
import mtr.data.Station;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public class RouteSelectionScreen extends BaseSelectionScreen {
    private final Consumer<List<RouteSelectInfo>> setter;
    private final BlockPos pos;
    private final Screen parent;

    private final Map<Long, Long> routePlatformMap = new HashMap<>();

    public RouteSelectionScreen(Component component, List<Long> defaultValue, Consumer<List<RouteSelectInfo>> setter, BlockPos pos, int maxSelect, Screen parent) {
        super(component, 2, maxSelect);
        this.setter = setter;
        this.pos = pos;
        this.titles = new ArrayList<>();
        this.parent = parent;
        titles.add(Component.translatable("ui.fangsu.common.selectPlat"));
        titles.add(Component.translatable("ui.fangsu.common.selectRoute"));
        titles.add(Component.translatable("ui.fangsu.common.selected"));
    }

    @Override
    public void updateColumn() {
        Station station = MtrUtil.getStationAt(pos.getCenter().toVector3f());
        if (station != null) {
            if (this.items != null)
                this.items.clear();
            else this.items = new ArrayList<>();
            Set<SelectionItem> platItemSet = new HashSet<>();
            Set<SelectionItem> routeItemSet = new HashSet<>();
            List<Platform> platforms = MtrUtil.getPlatformByStation(station);
            if (platforms != null)
                for (Platform p : platforms) {
                    String dest = MtrUtil.getDestinationByPlatform(p);
                    platItemSet.add(new SelectionItem(
                            p.name + " -> " + dest,
                            String.valueOf(p.id),
                            null
                    ));
                }
            if (selected.size() > 0 && selected.get(0) != null) {
                Long selectedPlat = Long.decode(selected.get(0));
                if (selectedPlat != null) {
                    Platform p = MtrUtil.getPlatformById(selectedPlat);
                    if (p != null) {
                        List<LocalRoute> routes = MtrUtil.getRouteByPlatform(p);
                        if (routes != null) {
                            for (LocalRoute r : routes) {
                                routePlatformMap.put(r.id, p.id);
                                String dest = MtrUtil.getDestinationByRoute(r);
                                routeItemSet.add(new SelectionItem(
                                        r.name + " -> " + dest,
                                        String.valueOf(r.id),
                                        ColorUtil.awtColorToMinecraft(Color.decode(String.valueOf(r.color)))
                                ));
                            }
                        }
                    }
                }
            }
            List<SelectionItem> itemPlat = new ArrayList<>(platItemSet);
            List<SelectionItem> itemRoute = new ArrayList<>(routeItemSet);
            this.items.add(itemPlat);
            this.items.add(itemRoute);
        }
    }

    @Override
    public void onClose() {
        List<RouteSelectInfo> v = new ArrayList<>();
        for (SelectionItem item : this.selectedItems) {
            Long routeId = Long.decode(item.value());
            v.add(new RouteSelectInfo(MtrUtil.getRouteById(routeId), MtrUtil.getPlatformById(routePlatformMap.get(routeId))));
        }
        this.setter.accept(v);
        Minecraft.getInstance().setScreen(parent);
    }

    public static final class RouteSelectInfo {
        public final LocalRoute route;
        public final Platform plat;

        public RouteSelectInfo(LocalRoute route, Platform plat) {
            this.route = route;
            this.plat = plat;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (RouteSelectInfo) obj;
            return Objects.equals(this.route, that.route) &&
                    Objects.equals(this.plat, that.plat);
        }

        @Override
        public int hashCode() {
            return Objects.hash(route, plat);
        }

        @Override
        public String toString() {
            return "RouteSelectInfo[" +
                    "route=" + route + ", " +
                    "plat=" + plat + ']';
        }

    }
}
