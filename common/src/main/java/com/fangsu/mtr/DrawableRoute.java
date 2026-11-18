package com.fangsu.mtr;

import com.fangsu.utils.MtrUtil;

import java.awt.*;
import java.util.*;
import java.util.List;

public class DrawableRoute {
    public final String routeName;
    public final Color routeColor;
    private final List<DrawableRouteStationInfo> stations;
    public final LocalRoute.CircularState circularState;

    // 运行区间在最终列表中的索引范围（包含两端）
    public final int beginIndexInclusive;
    private final int endIndexInclusive;
    private final int[] serviceToDrawing;

    private static final Map<Long, DrawableRoute> cacheRequestLongestRoute = Collections.synchronizedMap(new HashMap<>());

    /**
     * 清除缓存。在重进存档/世界重载时应调用此方法。
     */
    private static final java.util.concurrent.atomic.AtomicLong routeRevision = new java.util.concurrent.atomic.AtomicLong();

    public static void clearCache() {
        cacheRequestLongestRoute.clear();
        routeRevision.incrementAndGet();
    }

    public static long getRouteRevision() {
        return routeRevision.get();
    }

    public DrawableRoute(String routeName, Color routeColor, List<DrawableRouteStationInfo> stations,
                         LocalRoute.CircularState circularState, int beginIndexInclusive, int endIndexInclusive) {
        this(routeName, routeColor, stations, circularState, beginIndexInclusive, endIndexInclusive, null);
    }

    private DrawableRoute(String routeName, Color routeColor, List<DrawableRouteStationInfo> stations,
                          LocalRoute.CircularState circularState, int beginIndexInclusive, int endIndexInclusive,
                          int[] serviceToDrawing) {
        this.routeName = routeName;
        this.routeColor = routeColor;
        this.stations = stations;
        this.circularState = circularState;
        this.beginIndexInclusive = beginIndexInclusive;
        this.endIndexInclusive = endIndexInclusive;
        this.serviceToDrawing = serviceToDrawing;
    }

    public int drawingIndex(int serviceIndex) {
        if (serviceIndex < 0) return -1;
        if (serviceToDrawing == null) return Math.min(beginIndexInclusive + serviceIndex, endIndexInclusive + 1);
        return serviceIndex < serviceToDrawing.length ? serviceToDrawing[serviceIndex] : endIndexInclusive + 1;
    }

    /**
     * 根据列车当前位置（下一站的索引）动态生成带状态码的站点列表。
     *
     * @param currentIndex 列车当前所在的站点索引（即“下一站”在列表中的位置）
     * @return 用于绘制的站点列表，包含正确的 passingStatus
     */
    public List<DrawableRouteStation> getStations(int currentIndex) {
        List<DrawableRouteStation> result = new ArrayList<>(stations.size());
        for (int i = 0; i < stations.size(); i++) {
            DrawableRouteStationInfo info = stations.get(i);
            int status;
            if (i < beginIndexInclusive) {
                status = 0; // 区间外之前
            } else if (i > endIndexInclusive) {
                status = 4; // 区间外之后
            } else {
                // 运行区间内
                if (i < currentIndex) {
                    status = 1; // 已通过
                } else if (i == currentIndex) {
                    status = 2; // 下一站
                } else {
                    status = 3; // 未到达
                }
            }
            result.add(new DrawableRouteStation(info, status));
        }
        return result;
    }

    // ==================== 静态工厂方法 ====================

    public static DrawableRoute getDrawableRoute(LocalRoute route) {
        int lastIdx = Math.max(0, route.platformIds.size() - 1);
        return getDrawableRoute(route.asRouteDetail(), false, 0, lastIdx);
    }

    public static DrawableRoute getDrawableRoute(LocalRoute route, boolean isReversed) {
        int lastIdx = Math.max(0, route.platformIds.size() - 1);
        return getDrawableRoute(route.asRouteDetail(), isReversed, 0, lastIdx);
    }

    public static DrawableRoute getDrawableRoute(LocalRouteDetail routeDetail) {
        int lastIdx = Math.max(0, routeDetail.stationDetails.size() - 1);
        return getDrawableRoute(routeDetail, false, 0, lastIdx);
    }

    public static DrawableRoute getDrawableRoute(LocalRouteDetail routeDetail, boolean isReversed) {
        int lastIdx = Math.max(0, routeDetail.stationDetails.size() - 1);
        return getDrawableRoute(routeDetail, isReversed, 0, lastIdx);
    }

    /**
     * 核心构造方法：根据原始详情、方向、原始起止索引构建 DrawableRoute。
     *
     * @param routeDetail 路线详情
     * @param isReversed  是否反向
     * @param rawBegin    原始传入区间起点索引（正向列表中的位置）
     * @param rawEnd      原始传入区间终点索引（正向列表中的位置）
     * @return 可绘制路线对象
     */
    public static DrawableRoute getDrawableRoute(LocalRouteDetail routeDetail, boolean isReversed,
                                                 int rawBegin, int rawEnd) {
        List<LocalRouteDetail.StationDetails> source = routeDetail.stationDetails;
        int size = source.size();

        // 计算区间在正向列表中的实际起止（已排序）
        int forwardBegin = Math.min(rawBegin, rawEnd);
        int forwardEnd = Math.max(rawBegin, rawEnd);

        List<DrawableRouteStationInfo> stations = new ArrayList<>(size);

        // 根据方向确定遍历顺序
        int start = isReversed ? size - 1 : 0;
        int step = isReversed ? -1 : 1;

        // 用于记录最终列表中区间边界的索引
        int finalBegin = -1;
        int finalEnd = -1;

        for (int i = 0; i < size; i++) {
            int srcIdx = start + i * step;
            LocalRouteDetail.StationDetails detail = source.get(srcIdx);
            stations.add(new DrawableRouteStationInfo(detail.stationName, detail.transInfo, detail.stopStatus));

            // 判断该站在正向列表中的索引是否处于 [forwardBegin, forwardEnd] 区间内
            int forwardIdx = srcIdx; // 正向索引即原始列表索引
            if (forwardIdx >= forwardBegin && forwardIdx <= forwardEnd) {
                if (finalBegin == -1) {
                    finalBegin = i; // 第一个进入区间的站点在最终列表中的位置
                }
                finalEnd = i; // 持续更新最后一个区间站点位置
            }
        }

        // 若未找到区间边界（理论上不会发生，因为传入区间必定有效），则退化为整个列表
        if (finalBegin == -1) {
            finalBegin = 0;
            finalEnd = size - 1;
        }

        return new DrawableRoute(routeDetail.routeName, routeDetail.routeColor, stations,
                routeDetail.circularState, finalBegin, finalEnd);
    }

    public static DrawableRoute requestLongestRoute(Long routeId) {
        if (cacheRequestLongestRoute.containsKey(routeId)) return cacheRequestLongestRoute.get(routeId);
        LocalRoute route = MtrUtil.getRouteById(routeId);
        if (route == null) return null;
        return requestLongestRoute(route);
    }

    public static DrawableRoute requestLongestRoute(LocalRoute route) {
        if (cacheRequestLongestRoute.containsKey(route.id)) {
            return cacheRequestLongestRoute.get(route.id);
        }

        final long parentId = route.parentRouteId();
        if (parentId != 0) {
            final LocalRoute parent = MtrUtil.getRouteById(parentId);
            final int[] indices = parent == null ? null : matchParentStops(route, parent);
            if (indices != null && parent.asRouteDetail().stationDetails.size() == parent.platformIds.size()) {
                return cacheRoute(route, drawService(route, parent, indices, false));
            }
        }

        List<LocalRoute> candidates = MtrUtil.getRouteByName(route.name);
        if (candidates == null) {
            candidates = Collections.emptyList();
        }

        LocalRoute longestRoute = route;
        boolean reversed = false;
        int[] indices = null;

        if (!route.platformIds.isEmpty()) {
            for (final LocalRoute candidate : candidates) {
                if (candidate.id == route.id || candidate.platformIds.size() < longestRoute.platformIds.size()) continue;
                final int[] forward = matchStops(route, candidate, false);
                final boolean reverseCandidate = forward == null;
                final int[] match = reverseCandidate ? matchStops(route, candidate, true) : forward;
                if (match != null && (candidate.platformIds.size() > longestRoute.platformIds.size() || reversed && !reverseCandidate)) {
                    longestRoute = candidate;
                    reversed = reverseCandidate;
                    indices = match;
                }
            }
        }

        if (indices == null || longestRoute.asRouteDetail().stationDetails.size() != longestRoute.platformIds.size()) {
            final var result = getDrawableRoute(route.asRouteDetail());
            return parentId == 0 ? cacheRoute(route, result) : result;
        }
        // 显式母线的站序还没同步时保留原先的同名线路图，等同步后重新对齐。
        final var result = drawService(route, longestRoute, indices, reversed);
        return parentId == 0 ? cacheRoute(route, result) : result;
    }

    private static DrawableRoute drawService(LocalRoute route, LocalRoute longestRoute, int[] indices, boolean reversed) {
        final var display = longestRoute.asRouteDetail();
        final var stations = new ArrayList<>(display.stationDetails);
        int displayIndex = indices[0];
        final int step = reversed ? -1 : 1;
        for (int localIndex = 0; localIndex < indices.length; localIndex++) {
            final int target = indices[localIndex];
            while (displayIndex != target) {
                final var station = stations.get(displayIndex);
                stations.set(displayIndex, new LocalRouteDetail.StationDetails(station.stationName, station.transInfo, RouteStopStatus.DISABLED));
                displayIndex += step;
            }
            final var station = stations.get(target);
            stations.set(target, new LocalRouteDetail.StationDetails(station.stationName, station.transInfo,
                    route.platformIds.get(localIndex).stopStatus));
            displayIndex += step;
        }

        final var detail = new LocalRouteDetail(display.routeName, display.routeColor.getRGB(), display.circularState,
                display.currentStationIndex, stations);
        final var chart = getDrawableRoute(detail, reversed, indices[0], indices[indices.length - 1]);
        if (reversed) {
            for (int i = 0; i < indices.length; i++) indices[i] = stations.size() - 1 - indices[i];
        }
        return new DrawableRoute(chart.routeName, chart.routeColor, chart.stations, chart.circularState,
                chart.beginIndexInclusive, chart.endIndexInclusive, indices);
    }

    /** 站台替换不改变站点身份，环线重复经过同一座车站时也按对应的母线条目对齐。 */
    private static int[] matchParentStops(LocalRoute service, LocalRoute parent) {
        final int[] result = new int[service.platformIds.size()];
        int parentIndex = 0;
        for (int i = 0; i < result.length; i++) {
            final var linked = service.platformIds.get(i).serviceStop;
            if (linked == null || linked.parentRoute() != parent.id || linked.parentStop() == null) return null;
            while (parentIndex < parent.platformIds.size()) {
                final var candidate = parent.platformIds.get(parentIndex).serviceStop;
                if (candidate != null && linked.parentStop().equals(candidate.id())) break;
                parentIndex++;
            }
            if (parentIndex >= parent.platformIds.size()) return null;
            result[i] = parentIndex++;
        }
        return result;
    }

    /** 保留每个站台出现的位置，长短交路中间有缺站或同站台出现两次时也能对齐。 */
    private static int[] matchStops(LocalRoute service, LocalRoute drawing, boolean reversed) {
        final int step = reversed ? -1 : 1;
        int index = reversed ? drawing.platformIds.size() - 1 : 0;
        final int[] result = new int[service.platformIds.size()];
        for (int i = 0; i < result.length; i++) {
            final long id = service.platformIds.get(i).platformId;
            if (id == 0) return null;
            while (index >= 0 && index < drawing.platformIds.size() && drawing.platformIds.get(index).platformId != id) {
                index += step;
            }
            if (index < 0 || index >= drawing.platformIds.size()) return null;
            result[i] = index;
            index += step;
        }
        return result;
    }

    private static DrawableRoute cacheRoute(LocalRoute route, DrawableRoute result) {
        cacheRequestLongestRoute.put(route.id, result);
        return result;
    }

    // ==================== 内部数据类 ====================

    public record DrawableRouteStationInfo(String stationName, List<ColorNameTuple> transInfo, RouteStopStatus stopStatus) {
        public DrawableRouteStationInfo(String stationName, List<ColorNameTuple> transInfo) {
            this(stationName, transInfo, RouteStopStatus.NORMAL);
        }
    }

    public static class DrawableRouteStation {
        public final String stationName;
        public final List<ColorNameTuple> transInfo;
        public final RouteStopStatus stopStatus;
        /**
         * 0 = 运行区间之前
         * 1 = 已通过
         * 2 = 下一站/本站
         * 3 = 未到达
         * 4 = 运行区间之后
         */
        public final int passingStatus;

        public DrawableRouteStation(String stationName, List<ColorNameTuple> transInfo, int passingStatus) {
            this(new DrawableRouteStationInfo(stationName, transInfo), passingStatus);
        }

        public DrawableRouteStation(DrawableRouteStationInfo info, int passingStatus) {
            this.stationName = info.stationName();
            this.transInfo = info.transInfo() == null ? List.of() : info.transInfo();
            this.stopStatus = info.stopStatus();
            this.passingStatus = passingStatus;
        }
    }
}
